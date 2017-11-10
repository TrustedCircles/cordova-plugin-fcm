#import <UIKit/UIKit.h>
#import <Cordova/CDVPlugin.h>
#import <CoreLocation/CoreLocation.h>

@interface FCMPlugin : CDVPlugin <CLLocationManagerDelegate>
{
    CLLocationManager *locationManager;
    //NSString *notificationCallBack;
}

+ (FCMPlugin *) fcmPlugin;
- (void)ready:(CDVInvokedUrlCommand*)command;
- (void)showNotification:(CDVInvokedUrlCommand *)command;
- (void)dismissNotification:(CDVInvokedUrlCommand *)command;
- (void)getGServicesStatus:(CDVInvokedUrlCommand*)command;
- (void)logEvent:(CDVInvokedUrlCommand*)command;
- (void)getToken:(CDVInvokedUrlCommand*)command;
- (void)subscribeToTopic:(CDVInvokedUrlCommand*)command;
- (void)unsubscribeFromTopic:(CDVInvokedUrlCommand*)command;
- (void)registerNotification:(CDVInvokedUrlCommand*)command;
- (void)notifyOfMessage:(NSData*) payload;
- (void)notifyOfTokenRefresh:(NSString*) token;
- (void)appEnterBackground;
- (void)appEnterForeground;

- (void)setLoggedIn:(CDVInvokedUrlCommand *)command;
- (Boolean)getLoggedIn;
- (void) setPreference:(CDVInvokedUrlCommand *)command;
- (NSString *) getPreference:(NSString *)key;
- (void)hasPermission;

- (void)initLocationManager:(NSDictionary *)userInfo;
- (void)stopLocationManager;

- (void)checkIfIsValidWarning:(CLLocation *) currentLocation;
- (void)showLocalNotification:(NSString *)title body:(NSString *)body type:(NSString *)type userInfo:(NSDictionary *)userInfo;
- (void)replaceNotificationIfNeeded:(NSString *)key;
- (void)dismissNotificationByTimestamp:(NSString *)timestamp;

@end
