//
//  AppDelegate+FCMPlugin.m
//  TestApp
//
//  Created by felipe on 12/06/16.
//
//
#import "AppDelegate+FCMPlugin.h"
#import "FCMPlugin.h"
#import <objc/runtime.h>
#import <Foundation/Foundation.h>

#import "Firebase.h"

#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
@import UserNotifications;
#endif

@import FirebaseInstanceID;
@import FirebaseMessaging;

// Implement UNUserNotificationCenterDelegate to receive display notification via APNS for devices
// running iOS 10 and above. Implement FIRMessagingDelegate to receive data message via FCM for
// devices running iOS 10 and above.
#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
@interface AppDelegate () <UNUserNotificationCenterDelegate, FIRMessagingDelegate>
//@interface AppDelegate : UIResponder <UIApplicationDelegate, UNUserNotificationCenterDelegate>
@end
#endif

// Copied from Apple's header in case it is missing in some cases (e.g. pre-Xcode 8 builds).
#ifndef NSFoundationVersionNumber_iOS_9_x_Max
#define NSFoundationVersionNumber_iOS_9_x_Max 1299
#endif


@implementation AppDelegate (MCPlugin)

static NSData *lastPush;
static NSMutableArray *pushList;

//Method swizzling
+ (void)load
{
    NSLog(@"load()");
    pushList = [NSMutableArray array];
    Method original =  class_getInstanceMethod(self, @selector(application:didFinishLaunchingWithOptions:));
    Method custom =    class_getInstanceMethod(self, @selector(application:customDidFinishLaunchingWithOptions:));
    method_exchangeImplementations(original, custom);
}

- (void) setApplicationInBackground:(NSNumber *) applicationInBackground {
    objc_setAssociatedObject(self, @"applicationInBackground", applicationInBackground, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSNumber *) applicationInBackground {
    return objc_getAssociatedObject(self,@"applicationInBackground");
}

// app is running and ready
- (BOOL)application:(UIApplication *)application customDidFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    
    [self application:application customDidFinishLaunchingWithOptions:launchOptions];

    [self grantPermissions];
    
    // [START configure_firebase]
    [FIRApp configure];
    // [END configure_firebase]
    // Add observer for InstanceID token refresh callback.
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(tokenRefreshNotification:)
                                                 name:kFIRInstanceIDTokenRefreshNotification object:nil];
    
    self.applicationInBackground = @(YES);
    
    return YES;
}

- (void) grantPermissions
{
    NSLog(@"grantPermissions()");
    // [START register_for_notifications]
    
    // (GRANT/)Register for remote notifications. This shows a permission dialog on first run, to
    // show the dialog at a more appropriate time move this registration accordingly.
    if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_7_1) {
        NSLog(@"receive message on iOS7.1");
        
        // iOS 7.1 or earlier. Disable the deprecation warnings.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
        UIRemoteNotificationType allNotificationTypes =
        (UIRemoteNotificationTypeSound |
         UIRemoteNotificationTypeAlert |
         UIRemoteNotificationTypeBadge);
        [[UIApplication sharedApplication] registerForRemoteNotificationTypes:allNotificationTypes];
#pragma clang diagnostic pop
    } else {
        // iOS 8 or later
        if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
            NSLog(@"receive message on iOS9_x");
            
            UIUserNotificationType allNotificationTypes =
            (UIUserNotificationTypeSound | UIUserNotificationTypeAlert | UIUserNotificationTypeBadge);
            UIUserNotificationSettings *settings =
            [UIUserNotificationSettings settingsForTypes:allNotificationTypes categories:nil];
            [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
        } else {
            NSLog(@"receive message on iOS10");
            // iOS 10 or later
#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
            
            UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert
            | UNAuthorizationOptionSound
            | UNAuthorizationOptionBadge;
            [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:authOptions completionHandler:^(BOOL granted, NSError * _Nullable error) {
                NSLog(@"permissions? %hhd", granted);
                
                if (error != nil){
                    NSLog(@"Error granting permissions: %@", error);
                }
                else{
                    NSLog(@"Permissions granted");
                }
            }];
            
            // Grant permissions for iOS 10 display notification (sent via APNS)
            [UNUserNotificationCenter currentNotificationCenter].delegate = self;
            
            // Grant permissions for iOS 10 data message (sent via FCM)
            [FIRMessaging messaging].remoteMessageDelegate = self;
#endif
        }
        
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    }
// [END register_for_notifications]
}

    // [START ios_10_data_message_handling]
#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
// Handle incoming notification messages while app is in the foreground.
/*- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler {
    // Print message ID.
    NSDictionary *userInfo = notification.request.content.userInfo;
    NSLog(@"willPresentNotification FOREGROUND");
    
    // Print full message.
    NSLog(@"%@", userInfo);
    
    NSError *error;
    NSDictionary *userInfoMutable = [userInfo mutableCopy];
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfoMutable
                                                       options:0
                                                         error:&error];
    [FCMPlugin.fcmPlugin notifyOfMessage:jsonData];
    [FCMPlugin.fcmPlugin showLocalNotification:userInfo[@"title"] body:userInfo[@"body"] type:@"type"];
    // Change this to your preferred presentation option
    completionHandler(UNNotificationPresentationOptionAlert);
}*/


// Handle notification messages after display notification is tapped by the user.
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
    didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)())completionHandler {
    NSDictionary *userInfo = response.notification.request.content.userInfo;

    NSError *error;
    NSDictionary *userInfoMutable = [userInfo mutableCopy];
    
    
    NSLog(@"New method with push callback: %@", userInfo);
    
    [userInfoMutable setValue:@(YES) forKey:@"wasTapped"];
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfoMutable
                                                       options:0
                                                         error:&error];
    NSLog(@"APP WAS CLOSED DURING PUSH RECEPTION Saved data: %@", jsonData);
    lastPush = jsonData;
    [pushList addObject:jsonData];
    
    completionHandler(UNNotificationPresentationOptionAlert);
}


// Receive data message on iOS 10 devices while app is in the foreground.
- (void)applicationReceivedRemoteMessage:(FIRMessagingRemoteMessage *)remoteMessage {
    // Print full message
    NSLog(@"new push on ios10: %@", remoteMessage.appData);
    
    NSDictionary *userInfo = remoteMessage.appData;
    NSError *error;
    NSDictionary *userInfoMutable = [userInfo mutableCopy];
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfoMutable
                                                       options:0
                                                         error:&error];
    if ([UIApplication sharedApplication].applicationState == UIApplicationStateActive) {
        //[FCMPlugin.fcmPlugin showLocalNotification:userInfo[@"title"] body:userInfo[@"body"] type:@"type"];
        NSLog(@"receivedRemoteFCMMessage() appStateActive");
        [FCMPlugin.fcmPlugin notifyOfMessage:jsonData];
    }
    else{
        NSLog(@"receivedRemoteFCMMessage() appState: %ld", (long)[UIApplication sharedApplication].applicationState);
        [FCMPlugin.fcmPlugin showLocalNotification:userInfo[@"title"] body:userInfo[@"body"] type:@"type"];
    }
}
#endif

// [END ios_10_data_message_handling]

// [START clicked_notifications]
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    NSLog(@"clicked notification: %@", userInfo);
    NSError *error;
    NSDictionary *userInfoMutable = [userInfo mutableCopy];
    [userInfoMutable setValue:@(YES) forKey:@"wasTapped"];
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfoMutable
                                                       options:0
                                                         error:&error];
    
    if (application.applicationState != UIApplicationStateActive) {
        NSLog(@"user clicked a notification: %@", userInfo);
        lastPush = jsonData;
        [pushList addObject:jsonData];
    }
    else{
        NSLog(@"clicked notification() state: %d", application.applicationState);
    }
}
//#endif
// [END clicked_notification]

// [START receive_message in background or foreground, up to iOS10]
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo
    fetchCompletionHandler:(void (^)(UIBackgroundFetchResult result))completionHandler
{
    // If you are receiving a notification message while your app is in the background | foreground,
    // you have 30s to process any task.

    // Pring full message.
    NSLog(@"didReceiveRemoteNotification.fetchCompletion: %@", userInfo);
    NSError *error;
    
    NSDictionary *userInfoMutable = [userInfo mutableCopy];
    
    // NOTE: a push directly through apple will have the fields under data.* available
    // through firebase direct from the root of the object, @"title"
    
    NSString *type = userInfo[@"type"];
    
	//USER NOT TAPPED NOTIFICATION
    if (application.applicationState == UIApplicationStateActive) {
        NSLog(@"Remote notification. app active");
        [userInfoMutable setValue:@(NO) forKey:@"wasTapped"];
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfoMutable
                                                           options:0
                                                             error:&error];
        // app state active: on ios10 willPresentNotification handles Foreground notifications
        // So avoid to process it twice for iOS10
        //if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
            //NSLog(@"Remote notification. app active on < iOS10");
            if ([type isEqualToString:@"6"]){
                [FCMPlugin.fcmPlugin initLocationManager:userInfo];
            }
            else{
                [FCMPlugin.fcmPlugin showLocalNotification:userInfo[@"title"] body:userInfo[@"body"] type:@"type"];
                [FCMPlugin.fcmPlugin notifyOfMessage:jsonData];
            }
    // app is in background or in standby (NOTIFICATION WILL BE TAPPED)
    }else if (application.applicationState == UIApplicationStateBackground) {
        NSLog(@"Remote notification. app in background");
        
        [userInfoMutable setValue:@(YES) forKey:@"wasTapped"];
        
        if ([type isEqualToString:@"6"]){
            [FCMPlugin.fcmPlugin initLocationManager:userInfo];
        }
        else{
            [FCMPlugin.fcmPlugin showLocalNotification:userInfo[@"title"] body:userInfo[@"body"] type:@"type"];
        }
    }else{
        [userInfoMutable setValue:@(YES) forKey:@"wasTapped"];
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:userInfoMutable
                                                           options:0
                                                             error:&error];
        //NSLog(@"APP WAS CLOSED DURING PUSH RECEPTION Saved data: %@", jsonData);
        NSLog(@"Remote notification. OTHER STATE?");
        [FCMPlugin.fcmPlugin showLocalNotification:userInfo[@"title"] body:userInfo[@"body"] type:@"type"];
        lastPush = jsonData;
        [pushList addObject:jsonData];
    }

    completionHandler(UIBackgroundFetchResultNewData);
}
// [END receive_message in background or foreground]

//#endif

// [START refresh_token]
- (void)tokenRefreshNotification:(NSNotification *)notification
{
    
    // Note that this callback will be fired everytime a new token is generated, including the first
    // time. So if you need to retrieve the token as soon as it is available this is where that
    // should be done.
    NSString *refreshedToken = [[FIRInstanceID instanceID] token];
    if (refreshedToken == nil){
        return;
    }
    NSLog(@"tokenRefreshed. InstanceID token: %@", refreshedToken);
    
    // Connect to FCM since connection may have failed when attempted before having a token.
    [self connectToFcm];
}
// [END refresh_token]

// [START connect_to_fcm]
- (void)connectToFcm
{
    // Won't connect since there is no token
    if (![[FIRInstanceID instanceID] token]) {
        return;
    }
    
    [[FIRMessaging messaging] disconnect];
    
    [[FIRMessaging messaging] connectWithCompletion:^(NSError * _Nullable error) {
        if (error != nil) {
            NSLog(@"Unable to connect to FCM. %@", error);
        } else {
            NSLog(@"Connected to FCM.");
            [[FIRMessaging messaging] subscribeToTopic:@"/topics/ios"];
            [[FIRMessaging messaging] subscribeToTopic:@"/topics/all"];
        }
    }];
    
    [FCMPlugin.fcmPlugin hasPermission];
}
// [END connect_to_fcm]

- (void)applicationDidBecomeActive:(UIApplication *)application
{
    NSLog(@"app become active");
    //[self showLocalNotification:@"test22" body:@"body11" type:@"type"];
    self.applicationInBackground = @(NO);
    [FCMPlugin.fcmPlugin appEnterForeground];
    [self connectToFcm];
}

// [START disconnect_from_fcm]
- (void)applicationDidEnterBackground:(UIApplication *)application
{
    NSLog(@"app entered background");
    [[FIRMessaging messaging] disconnect];
    [FCMPlugin.fcmPlugin appEnterBackground];
    self.applicationInBackground = @(YES);
    NSLog(@"Disconnected from FCM");
}
// [END disconnect_from_fcm]

+(NSData*)getLastPush
{
    NSData* returnValue = lastPush;
    lastPush = nil;
    return returnValue;
}

+(NSArray*) getPushList
{
    NSArray * p = pushList;
    [pushList removeAllObjects];
    return p;
}

// This function is added here only for debugging purposes, and can be removed if swizzling is enabled.
// If swizzling is disabled then this function must be implemented so that the APNs token can be paired to
// the InstanceID token.
- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
    /*const unsigned *tokenBytes = [deviceToken bytes];
    NSString *hexToken = [NSString stringWithFormat:@"%08x%08x%08x%08x%08x%08x%08x%08x",
                          ntohl(tokenBytes[0]), ntohl(tokenBytes[1]), ntohl(tokenBytes[2]), ntohl(tokenBytes[3]),
                          ntohl(tokenBytes[4]), ntohl(tokenBytes[5]), ntohl(tokenBytes[6]), ntohl(tokenBytes[7])];
    
    NSLog(@"APNs token retrieved: %@", hexToken);
    */
    // MANDATORY: With FirebaseAppDelegateProxyEnabled === NO, set the token
    // With swizzling disabled you must set the APNs token here.
    
    // TypeUnknown: let firebase choose what token to use.
    // TypeProd problem: token null continuously. github.com/firebase/quickstart-ios/issues/47
    [[FIRInstanceID instanceID] setAPNSToken:deviceToken type:FIRInstanceIDAPNSTokenTypeUnknown];
}

- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    NSLog(@"Unable to register for remote notifications: %@", error);
}

@end
