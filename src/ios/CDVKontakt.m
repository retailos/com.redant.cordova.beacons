#include <sys/types.h>
#include <sys/sysctl.h>
#import <Cordova/CDV.h>

#import "CDVKontakt.h"
#import "AFNetworking.h"

@interface CDVKontakt () {}
@end

@implementation CDVKontakt

- (void)initMonitoringBeacons:(CDVInvokedUrlCommand*)command {
    
    self.apiKey = [command.arguments objectAtIndex:0];
    self.sensitivity = [[command.arguments objectAtIndex:1] integerValue];
    self.callbackId = command.callbackId;
    
    self.client = [KTKClient new];
    [_client setApiKey:_apiKey];
    
    self.actionManager = [KTKActionManager new];
    _actionManager.delegate = self;
    
    self.beacons = [[NSMutableArray alloc] initWithObjects: nil];
    
    KTKRegion *region = [[KTKRegion alloc] init];
    region.uuid = @"f7826da6-4fa2-4e98-8024-bc5b71e0893e"; // kontakt.io proximity UUID
    
    if ([KTKLocationManager canMonitorBeacons]) {
        self.locationManager = [KTKLocationManager new];
        _locationManager.delegate = self;
        [self.locationManager setRegions:@[region]];
        [self.locationManager startMonitoringBeacons];
    }
}

- (void)getDataForBeacon:(CLBeacon *)beacon withSuccessBlock:(void (^)(AFHTTPRequestOperation *operation, id responseObject))success {
    
    NSString *strURL = [NSString stringWithFormat:@"https://api.kontakt.io/beacon?proximity=%@&major=%@&minor=%@",[beacon.proximityUUID UUIDString],beacon.major,beacon.minor];
    
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:strURL]];
    
    [request setValue:_apiKey forHTTPHeaderField:@"Api-Key"];
    [request setValue:@"application/vnd.com.kontakt+json; version=2" forHTTPHeaderField:@"Accept"];
    
    AFHTTPRequestOperation *operation = [[AFHTTPRequestOperation alloc] initWithRequest:request];
    
    [operation setCompletionBlockWithSuccess:success failure:^(AFHTTPRequestOperation *operation, NSError *error) {
        NSLog(@"faliure to download beacon info: %@", error);
    }];
    
    [operation start];
}

#pragma mark - KTKLocationManagerDelegate method

- (void)locationManager:(KTKLocationManager *)locationManager didRangeBeacons:(NSArray *)beacons {
    
    CLBeacon *beacon = [beacons firstObject];
    
    if ((long)beacon.rssi != 0 && (long)beacon.rssi > self.sensitivity) {
        
        NSLog(@"%@: %ld", beacon.major, (long)beacon.rssi);
        
        [self getDataForBeacon:beacon withSuccessBlock:^(AFHTTPRequestOperation *operation, id responseObject) {
            
            NSError * error=nil;
            NSDictionary *beaconProperties = [NSJSONSerialization JSONObjectWithData:responseObject options:0 error:&error];

            if (beaconProperties && self.callbackId) {
                
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:beaconProperties];
                [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
            }
        }];
    }
}

- (void)locationManager:(KTKLocationManager *)locationManager didChangeState:(KTKLocationManagerState)state withError:(NSError *)error {
    
}

- (void)locationManager:(KTKLocationManager *)locationManager didEnterRegion:(KTKRegion *)region {

}

- (void)locationManager:(KTKLocationManager *)locationManager didExitRegion:(KTKRegion *)region {

}

@end
