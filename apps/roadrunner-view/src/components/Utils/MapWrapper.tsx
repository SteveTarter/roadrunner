export class MapWrapper<K, V> {

  wrappedMap: Map<K, V> = new Map<K, V>();

   public set(key: K, value: V) {
    this.wrappedMap.set(key, value);
  }

  public get(key: K) {
    return this.wrappedMap.get(key);
  }

  public delete(key: K) {
    this.wrappedMap.delete(key)
  }

  public forEach(callbackfn: any) {
    this.wrappedMap.forEach(callbackfn);
  }

  public clear() {
    this.wrappedMap.clear();
  }
}