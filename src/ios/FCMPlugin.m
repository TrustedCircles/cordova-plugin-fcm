#include <sys/types.h>
#include <sys/sysctl.h>

#import "AppDelegate+FCMPlugin.h"

#import <Cordova/CDV.h>
#import "FCMPlugin.h"
#import "Firebase.h"
#import <CoreLocation/CoreLocation.h>


@interface FCMPlugin () {}
@end

@implementation FCMPlugin

static BOOL notificatorReceptorReady = NO;
static BOOL appInForeground = YES;

static NSString *notificationCallback = @"FCMPlugin.onNotificationReceived";
static NSString *tokenRefreshCallback = @"FCMPlugin.onTokenRefreshReceived";

static NSString *TYPE_WARNING = @"6";

static FCMPlugin *fcmPluginInstance;

+ (FCMPlugin *) fcmPlugin {
    
    return fcmPluginInstance;
}

- (void) ready:(CDVInvokedUrlCommand *)command
{
    NSLog(@"Cordova view ready");
    fcmPluginInstance = self;
    [self.commandDelegate runInBackground:^{
        
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
    
}

- (void) showNotification:(CDVInvokedUrlCommand *)command
{
    NSString* title = [command.arguments objectAtIndex:0];
    NSString* body = [command.arguments objectAtIndex:1];
    [self showLocalNotification:title body:body type:@"999" userInfo:nil];
}

- (void) dismissNotification:(CDVInvokedUrlCommand *)command
{
    NSString* timestamp = [command.arguments objectAtIndex:0];
    if (timestamp != nil){
        [self dismissNotificationByTimestamp:timestamp];
    }
}

- (void) getGServicesStatus:(CDVInvokedUrlCommand *)command
{
    // dummy
}

- (void) logEvent:(CDVInvokedUrlCommand *)command
{
    //[FIRAnalytics logEventWithName:[command.arguments objectAtIndex:0]
    //                    parameters: [command.arguments objectAtIndex:1]];
}

// GET TOKEN //
- (void) getToken:(CDVInvokedUrlCommand *)command
{
    NSLog(@"get Token");
    [self.commandDelegate runInBackground:^{
        NSString* token = [[FIRInstanceID instanceID] token];
        NSLog(@"device token: %@", token);
        
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:token];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

// UN/SUBSCRIBE TOPIC //
- (void) subscribeToTopic:(CDVInvokedUrlCommand *)command 
{
    NSString* topic = [command.arguments objectAtIndex:0];
    NSLog(@"subscribe To Topic %@", topic);
    [self.commandDelegate runInBackground:^{
        if(topic != nil)[[FIRMessaging messaging] subscribeToTopic:[NSString stringWithFormat:@"/topics/%@", topic]];
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:topic];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void) unsubscribeFromTopic:(CDVInvokedUrlCommand *)command 
{
    NSString* topic = [command.arguments objectAtIndex:0];
    NSLog(@"unsubscribe From Topic %@", topic);
    [self.commandDelegate runInBackground:^{
        if(topic != nil)[[FIRMessaging messaging] unsubscribeFromTopic:[NSString stringWithFormat:@"/topics/%@", topic]];
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:topic];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void) hasPermission/*:(CDVInvokedUrlCommand *) command*/
{
    UIApplication *application = [UIApplication sharedApplication];
    if ([[UIApplication sharedApplication] respondsToSelector:@selector(registerUserNotificationSettings:)]){
        NSLog(@"PERMISSIONS GRANTED");
    }
    else{
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
        NSLog(@"PERMISSIONS KO :(: %d", application.enabledRemoteNotificationTypes != UIRemoteNotificationTypeNone);
#pragma FCC diagnostic pop
    }
}

- (void) registerNotification:(CDVInvokedUrlCommand *)command
{
    NSLog(@"view registered for notifications");
    
    notificatorReceptorReady = YES;
    NSData* lastPush = [AppDelegate getLastPush];
    if (lastPush != nil) {
        [FCMPlugin.fcmPlugin notifyOfMessage:lastPush];
    }
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void) notifyOfMessage:(NSData *)payload
{
    NSString *JSONString = [[NSString alloc] initWithBytes:[payload bytes] length:[payload length] encoding:NSUTF8StringEncoding];
    NSString * notifyJS = [NSString stringWithFormat:@"%@(%@);", notificationCallback, JSONString];
    NSLog(@"notifyOfMessage: %@", notifyJS);
    
    NSString *jsonStr;
    NSArray *pushList = [AppDelegate getPushList];
    NSLog(@"pushList: %lu", (unsigned long)pushList.count);
    
    for (NSData *jsonData in pushList){
        jsonStr = [[NSString alloc] initWithBytes:[jsonData bytes] length:[jsonData length] encoding:NSUTF8StringEncoding];
        //TODO: send to the view
        NSLog(@"notifyOfMessage to the view: %@", jsonStr);
    }
    if ([self.webView respondsToSelector:@selector(stringByEvaluatingJavaScriptFromString:)]) {
        [(UIWebView *)self.webView stringByEvaluatingJavaScriptFromString:notifyJS];
    } else {
        [self.webViewEngine evaluateJavaScript:notifyJS completionHandler:nil];
    }
}

-(void) notifyOfTokenRefresh:(NSString *)token
{
    NSString * notifyJS = [NSString stringWithFormat:@"%@('%@');", tokenRefreshCallback, token];
    NSLog(@"stringByEvaluatingJavaScriptFromString %@", notifyJS);
    
    if ([self.webView respondsToSelector:@selector(stringByEvaluatingJavaScriptFromString:)]) {
        [(UIWebView *)self.webView stringByEvaluatingJavaScriptFromString:notifyJS];
    } else {
        [self.webViewEngine evaluateJavaScript:notifyJS completionHandler:nil];
    }
}

-(void) appEnterBackground
{
    NSLog(@"Set state background");
    appInForeground = NO;
}

-(void) appEnterForeground
{
    NSLog(@"Set state foreground");
    NSData* lastPush = [AppDelegate getLastPush];
    if (lastPush != nil) {
        [FCMPlugin.fcmPlugin notifyOfMessage:lastPush];
    }
    appInForeground = YES;
}

- (void) setPreference:(CDVInvokedUrlCommand *)command
{
    NSString *key = [command.arguments objectAtIndex:0];
    NSString *value = [command.arguments objectAtIndex:1];
    NSLog(@"setPreference(): %@ - args: %@", key, command.arguments);
    if (key == nil || value == nil){
        NSLog(@"setPreference() invalid values");
        return;
    }
    
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    [prefs setObject:value forKey:key];
    [prefs synchronize];
    
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:key];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (NSString *) getPreference:(NSString *) key
{
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    NSString *value = [prefs stringForKey:key];
    NSLog(@"getPreference(): %@ - %@", key, value);
    
    return value;
}

- (void) setLoggedIn:(CDVInvokedUrlCommand *)command
{
    NSString *str_status = [command.arguments objectAtIndex:0];
    NSLog(@"SetLoggedIn(): %@ - args: %@", str_status, command.arguments);
    
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    // TODO: [[FIRMessaging messaging] disconnect];
    [prefs setBool:[str_status boolValue] forKey:@"login_status"];
    [prefs synchronize];
}

- (Boolean) getLoggedIn
{
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    Boolean status = [prefs boolForKey:@"login_status"];
    NSLog(@"getLoggedIn(): %hhu", status);
    
    return status;//[status isEqualToString:@"true"];
}






/************* geolocation **************/
 


CLLocation *warnLoc = nil;
NSMutableDictionary *warnData = nil;
Boolean checkingLocation = NO;
-(void) initLocationManager:(NSDictionary *)userInfo
{
    if (warnLoc != nil){
        //[warnLoc release];
        warnLoc = nil;
        warnData = nil;
    }
    NSLog(@"initLocationManager() Position of the warning: %@", [userInfo valueForKeyPath:@"position"]);
    NSString *positionStr = [userInfo valueForKeyPath:@"position"];
    id remote_pos = [NSJSONSerialization JSONObjectWithData:[positionStr dataUsingEncoding:NSUTF8StringEncoding] options:0 error:nil];
    NSLog(@"latitude: %@", [remote_pos objectForKey:@"latitude"]);
    
    NSString *warnTimestamp = [userInfo valueForKeyPath:@"timestamp"];
    if (warnTimestamp == nil){
        NSLog(@"malformed warning, no timestamp");
        return;
    }
    NSDate *now = [NSDate date];
    NSDate *warnTime = [NSDate dateWithTimeIntervalSince1970:[warnTimestamp doubleValue]];
    NSTimeInterval warnDif = [now timeIntervalSinceDate:warnTime];
    NSLog(@"warning sent %d secs ago", (int)warnDif);
    if ((warnDif / 3600) > 3){
        NSLog(@"warning TOO old, discarding");
        return;
    }
    
    warnData = [userInfo mutableCopy];
    [warnData setValue:[NSString stringWithFormat:@"%d", ((int)warnDif/60)] forKey:@"sent_time"];
    
    warnLoc = [[CLLocation alloc]
               initWithLatitude:[[remote_pos objectForKey:@"latitude"] doubleValue]
                       longitude:[[remote_pos objectForKey:@"longitude"] doubleValue]];
    
    locationManager = [[CLLocationManager alloc] init];
    [locationManager requestWhenInUseAuthorization];
    
    //locationManager = [CLLocationManager new];
    locationManager.delegate = self;
    locationManager.distanceFilter = kCLDistanceFilterNone;
    locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters;
    locationManager.allowsBackgroundLocationUpdates = YES;
    locationManager.pausesLocationUpdatesAutomatically = NO;
    locationManager.activityType = CLActivityTypeFitness;
    [locationManager disallowDeferredLocationUpdates];
    [locationManager startUpdatingLocation];
 
    [NSTimer scheduledTimerWithTimeInterval:60.0
                                     target:self
                                   selector:@selector(stopLocationManager)
                                   userInfo:nil repeats:NO];
    
    NSLog(@"initLocationManager() getting GPS position...");
}

-(void) stopLocationManager
{
    checkingLocation = NO;
    warnLoc = nil;
    warnData = nil;
    [locationManager stopUpdatingLocation];
    
    NSLog(@"stopLocationManager");
}

- (void)locationManager:(CLLocationManager *)manager didUpdateToLocation:(CLLocation *)newLocation fromLocation:(CLLocation *)oldLocation {
    NSLog(@"OldLocation %f %f %f", oldLocation.coordinate.latitude, oldLocation.coordinate.longitude, oldLocation.horizontalAccuracy);
    NSLog(@"NewLocation %f %f %f", newLocation.coordinate.latitude, newLocation.coordinate.longitude, newLocation.horizontalAccuracy);
    
    [self checkIfIsValidWarning:newLocation];
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray<CLLocation *> *)locations
{
    NSLog(@"didUpdateLocations: %lu", (unsigned long)[locations count]);
    [self checkIfIsValidWarning:[locations objectAtIndex:0]];
}

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error
{
    NSLog(@"locationManager error: %@", error);
    [self stopLocationManager];
}

- (void)checkIfIsValidWarning:(CLLocation *) currentLocation
{
    if (checkingLocation == YES){
        NSLog(@"checkIfIsValidWarning: ALREADY CHEKING");
        return;
    }
    checkingLocation = YES;
    
    CLLocationDistance dist = [warnLoc distanceFromLocation:currentLocation];
    if (dist < 1500.0){
        NSLog(@"checkIfIsValidWarning. VALID: %f", dist);
        [self showLocalNotification:@"Aviso de ayuda urgente" body:[NSString stringWithFormat:@"Alguien solicita ayuda urgente\nEnviado hace %@ mins.",warnData[@"sent_time"]] type:TYPE_WARNING userInfo:warnData];
        if (warnData != nil){
            [warnData setValue:[NSString stringWithFormat:@"{\"latitude\": %f,\"longitude\": %f}", currentLocation.coordinate.latitude, currentLocation.coordinate.longitude] forKey:@"our_position"];
            NSLog(@"OUR POSITION: %@", warnData[@"our_position"]);
            NSData *data = [NSJSONSerialization dataWithJSONObject:warnData
                                                                          options:0
                                                                            error:nil];
            [self notifyOfMessage:data];
        }
    }
    else{
        NSLog(@"checkIfIsValidWarning. INVALID: %f", dist);
    }
    [self stopLocationManager];
}

- (void) showLocalNotification:(NSString *)title body:(NSString *)body type:(NSString *)type userInfo:(NSDictionary *)userInfo
{
    if (![self getLoggedIn]){
        NSLog(@"Not logged in");
        return;
    }
    
/*#if defined(__IPHONE_10_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_10_0
     UNMutableNotificationContent *content = [UNMutableNotificationContent new];
     content.title = title;
     content.body = body;
     content.sound = [UNNotificationSound defaultSound];
     UNTimeIntervalNotificationTrigger *trigger = [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:10 repeats:NO];
     UNNotificationRequest *req = [UNNotificationRequest requestWithIdentifier:@"xx" content:content trigger:trigger];
     
     UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
     [center addNotificationRequest:req withCompletionHandler:^(NSError * _Nullable error){
         if (error != nil){
            NSLog(@"oops: %@", error);
         }
     }];
#else*/
    // this works with deployment target == 8.0
    UILocalNotification *lc = [[UILocalNotification alloc] init];
    if (lc != nil && title != nil){
        lc.alertTitle = title;
        lc.alertBody = body;
        lc.userInfo = userInfo;
        //lc.timeZone = [NSTimeZone defaultTimeZone];
        lc.fireDate = [NSDate dateWithTimeIntervalSinceNow:5];
        //lc.applicationIconBadgeNumber = [[UIApplication sharedApplication] applicationIconBadgeNumber] + 1;
        /*if ([type isEqualToString:TYPE_WARNING]){
            lc.repeatInterval = kCFCalendarUnitSecond;
        }*/
        [self replaceNotificationIfNeeded:userInfo[@"notificationId"]];
        [[UIApplication sharedApplication] scheduleLocalNotification:lc];
    }
    else{
        NSLog(@"showLocalNotification: not valid");
    }
//#endif
}

- (void)replaceNotificationIfNeeded:(NSString *)key
{

    //UILocalNotification *notifToCancel=nil;
    for(UILocalNotification *aNotif in [[UIApplication sharedApplication] scheduledLocalNotifications])
    {
        //NSLog(@"is repeated? %@", key);
        if ([[aNotif.userInfo objectForKey:@"notificationId"] isEqualToString:key]){
            NSLog(@"Canceling notification with key, remote: %@ - local: %@", key, [aNotif.userInfo objectForKey:@"notificationId"]);
            [[UIApplication sharedApplication] cancelLocalNotification:aNotif];
            return;
        }
        
        NSLog(@"notification not repeated");
    }
}

- (void)dismissNotificationByTimestamp:(NSString *)timestamp
{
    
    //UILocalNotification *notifToCancel=nil;
    for(UILocalNotification *aNotif in [[UIApplication sharedApplication] scheduledLocalNotifications])
    {
        //NSLog(@"is repeated? %@", timestamp);
        if ([[aNotif.userInfo objectForKey:@"timestamp"] isEqualToString:timestamp]){
            NSLog(@"Canceling notification with timestamp, remote: %@ - local: %@", timestamp, [aNotif.userInfo objectForKey:@"timestamp"]);
            [[UIApplication sharedApplication] cancelLocalNotification:aNotif];
            return;
        }
        
        //NSLog(@"notification not found: %@", timestamp);
    }
}

@end
