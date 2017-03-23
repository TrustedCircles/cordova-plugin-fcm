cordova.define("cordova-plugin-fcm.FCMPlugin", function(require, exports, module) {

var exec = require('cordova/exec');

function FCMPlugin() { 
	console.log("FCMPlugin.js: is created");
}





// GET TOKEN //
FCMPlugin.prototype.getToken = function( success, error ){
	exec(success, error, "FCMPlugin", 'getToken', []);
}
    
FCMPlugin.prototype.onTokenRefresh = function( callback ){
    FCMPlugin.prototype.onTokenRefreshReceived = callback;
}

FCMPlugin.prototype.onTokenRefreshReceived = function(token){
    console.log("Received token refresh");
    console.log(token);
}
    
FCMPlugin.prototype.getGServicesStatus = function( success, error ){
	exec(success, error, "FCMPlugin", 'getGServicesStatus', []);
}
// SET LOGIN STATUS
FCMPlugin.prototype.setLoggedIn = function( uid, status, result ){
	exec(result, result, "FCMPlugin", 'setLoggedIn', [uid, status]);
}
// SET PREFERENCE
FCMPlugin.prototype.setPreference = function( key, value, result ){
	exec(result, result, "FCMPlugin", 'setPreference', [key, value]);
}
// SUBSCRIBE TO TOPIC //
FCMPlugin.prototype.subscribeToTopic = function( topic, success, error ){
	exec(success, error, "FCMPlugin", 'subscribeToTopic', [topic]);
}
// UNSUBSCRIBE FROM TOPIC //
FCMPlugin.prototype.unsubscribeFromTopic = function( topic, success, error ){
	exec(success, error, "FCMPlugin", 'unsubscribeFromTopic', [topic]);
}
// NOTIFICATION CALLBACK //
FCMPlugin.prototype.onNotification = function( callback, success, error ){
	FCMPlugin.prototype.onNotificationReceived = callback;
	exec(success, error, "FCMPlugin", 'registerNotification',[]);
}
//  //
FCMPlugin.prototype.logEvent = function( key, value, result ){
	exec(result, result, "FCMPlugin", 'logEvent', [key, value]);
}
// DEFAULT NOTIFICATION CALLBACK //
FCMPlugin.prototype.onNotificationReceived = function(payload){
	console.log("Received push notification")
	console.log(payload)
}

FCMPlugin.prototype.showNotification = function(title, body){
	exec(result, result, "FCMPlugin", 'showNotification', [title, body]);
}

// dismisses a notification by tag+id
FCMPlugin.prototype.dismissNotification = function( timestamp, id, success, error ){
	exec(success, error, "FCMPlugin", 'dismissNotification', [timestamp, id]);
}
// FIRE READY //
exec(function(result){ console.log("FCMPlugin Ready OK") }, function(result){ console.log("FCMPlugin Ready ERROR") }, "FCMPlugin",'ready',[]);





var fcmPlugin = new FCMPlugin();
module.exports = fcmPlugin;

});
