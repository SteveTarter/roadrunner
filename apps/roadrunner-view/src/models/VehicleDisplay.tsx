export class VehicleDisplay {
    circleRadius: number;
    popupVisible: boolean;
    routeVisible: boolean;

    constructor(
        circleRadius: number,
        popupVisible: boolean,
        routeVisible: boolean
    ) {
        this.circleRadius = circleRadius;
        this.popupVisible = popupVisible;
        this.routeVisible = routeVisible;
    }
}