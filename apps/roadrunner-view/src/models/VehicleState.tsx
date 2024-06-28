export class VehicleState {
    id: string;
    metersOffset: number;
    positionLimited: boolean;
    positionValid: boolean;
    degLatitude: number;
    degLongitude: number;
    metersPerSecondDesired: number;
    metersPerSecond: number;
    mssAcceleration: number;
    degBearing: number;
    colorCode: string;

    constructor(
        id: string,
        metersOffset: number,
        positionLimited: boolean,
        positionValid: boolean,
        degLatitude: number,
        degLongitude: number,
        metersPerSecondDesired: number,
        metersPerSecond: number,
        mssAcceleration: number,
        degBearing: number,
        colorCode: string
    ) {
        this.id = id;
        this.metersOffset = metersOffset;
        this.positionLimited = positionLimited;
        this.positionValid = positionValid;
        this.degLatitude = degLatitude;
        this.degLongitude = degLongitude;
        this.metersPerSecondDesired = metersPerSecondDesired;
        this.metersPerSecond = metersPerSecond;
        this.mssAcceleration = mssAcceleration;
        this.degBearing = degBearing;
        this.colorCode = colorCode;
    }
}
