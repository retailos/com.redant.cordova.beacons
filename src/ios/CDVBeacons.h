#import <UIKit/UIKit.h>
#import <Cordova/CDVPlugin.h>
#import <CoreLocation/CoreLocation.h>

@interface CDVBeacons : CDVPlugin <CLLocationManagerDelegate>

@property (strong, nonatomic) CLLocationManager *locationManager;
@property (strong, nonatomic) CLBeaconRegion *beaconRegion;

@property (nonatomic) NSInteger sensitivity;
@property (strong, nonatomic) NSString *callbackId;
    
- (void)initMonitoringBeacons:(CDVInvokedUrlCommand*)command;

@end
