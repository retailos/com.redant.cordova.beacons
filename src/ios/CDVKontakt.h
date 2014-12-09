#import <UIKit/UIKit.h>
#import <Cordova/CDVPlugin.h>
#import "KontaktSDK.h"
#import <CoreLocation/CoreLocation.h>

@interface CDVKontakt : CDVPlugin <KTKLocationManagerDelegate, KTKActionManagerDelegate>

@property (strong, nonatomic) KTKClient *client;
@property (strong, nonatomic) KTKLocationManager *locationManager;
@property (strong, nonatomic) KTKActionManager *actionManager;

@property (strong, nonatomic) NSString *apiKey;
@property (nonatomic) NSInteger sensitivity;
@property (strong, nonatomic) NSString *callbackId;
@property (strong, nonatomic) NSMutableArray *beacons;
    
- (void)initMonitoringBeacons:(CDVInvokedUrlCommand*)command;

@end
