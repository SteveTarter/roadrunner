export class VehicleDisplay {
  size: number;
  popupVisible: boolean;
  routeVisible: boolean;

  constructor(
    size: number,
    popupVisible: boolean,
    routeVisible: boolean
  ) {
    this.size = size;
    this.popupVisible = popupVisible;
    this.routeVisible = routeVisible;
  }
}