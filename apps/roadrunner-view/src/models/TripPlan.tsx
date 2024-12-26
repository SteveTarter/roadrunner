import { Address } from "./Address";

export class TripPlan {
  listStops: Address[];

  constructor(listStops: Address[]) {
    this.listStops = listStops;
  }
}