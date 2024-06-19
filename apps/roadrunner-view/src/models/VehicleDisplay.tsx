export class VehicleDisplay {
    circleRadius: number;
    routeVisible: boolean;

    constructor(
        circleRadius: number,
        routeVisible: boolean
    ) {
        this.circleRadius = circleRadius;
        this.routeVisible = routeVisible;
    }
}