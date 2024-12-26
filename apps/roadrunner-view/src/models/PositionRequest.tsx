import { TripPlan } from "./TripPlan";

export class PositionRequest {
  metersTravel: number;
  tripPlan: TripPlan;

  constructor(metersTravel: number, tripPlan: TripPlan) {
    this.metersTravel = metersTravel;
    this.tripPlan = tripPlan;
  }
}
