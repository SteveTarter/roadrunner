export class PositionResponse {
    valid: boolean;
    message: string;
    positionLimited: boolean;
    metersPerSecond: number;
    latitude: number;
    longitude: number;

    constructor(
        valid: boolean,
        message: string,
        positionLimited: boolean,
        metersPerSecond: number,
        latitude: number,
        longitude: number
    ) {
        this.valid = valid;
        this.message = message;
        this.positionLimited = positionLimited;
        this.metersPerSecond = metersPerSecond;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
