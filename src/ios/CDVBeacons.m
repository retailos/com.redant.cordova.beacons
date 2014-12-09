#include <sys/types.h>
#include <sys/sysctl.h>
#import <Cordova/CDV.h>

#import "CDVBeacons.h"

@interface CDVBeacons () {}
@end

@implementation CDVBeacons

- (void)initMonitoringBeacons:(CDVInvokedUrlCommand*)command {
    
    self.sensitivity = [[command.arguments objectAtIndex:0] integerValue];
    self.callbackId = command.callbackId;
    
    NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:@"f7826da6-4fa2-4e98-8024-bc5b71e0893e"];
    self.beaconRegion = [[CLBeaconRegion alloc] initWithProximityUUID:uuid identifier:@"Beacons"];
    
    self.locationManager = [CLLocationManager new];
    _locationManager.delegate = self;
    [_locationManager startMonitoringForRegion:_beaconRegion];
    [_locationManager startRangingBeaconsInRegion:_beaconRegion];
}

#pragma mark - CLLocationManagerDelegate method

- (void)locationManager:(CLLocationManager *)locationManager didRangeBeacons:(NSArray *)beacons inRegion:(CLBeaconRegion *)region {
    
    CLBeacon *beacon = [beacons firstObject];
    
    if ((long)beacon.rssi != 0 && (long)beacon.rssi > self.sensitivity) {
        
        NSLog(@"%@: %ld", beacon.major, (long)beacon.rssi);
        
        NSDictionary *beaconProperties = @{@"major": beacon.major, @"minor": beacon.minor, @"rssi": [NSNumber numberWithInteger:beacon.rssi]};
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:beaconProperties];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}


@end
