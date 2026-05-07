export class SimulationSession {
  id: string;
  userName: string;
  colorCode: string;
  start: string;
  end: string;


  constructor(
    id: string,
    userName: string,
    colorCode: string,
    start: string,
    end: string,
  ) {
    this.id = id;
    this.userName = userName;
    this.colorCode = colorCode;
    this.start = start;
    this.end = end;
  }
}