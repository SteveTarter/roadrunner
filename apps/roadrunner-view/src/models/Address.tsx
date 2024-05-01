export class Address {

    source: string;
    address1: string;
    address2: string;
    city: string;
    state: string;
    zipCode: string;

    latitude: number;
    longitude: number;

    constructor(
        source: string,
        address1: string,
        address2: string,
        city: string,
        state: string,
        zipCode: string,
        latitude: number,
        longitude: number
    ) {
        this.source = source;
        this.address1 = address1;
        this.address2 = address2;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}