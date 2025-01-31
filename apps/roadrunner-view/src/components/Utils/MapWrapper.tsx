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

  public values() {
    return this.wrappedMap.values();
  }

  public size() {
    return this.wrappedMap.size;
  }

  public filter(predicate: (value: V, key: K, map: Map<K, V>) => boolean): MapWrapper<K, V> {
    const filteredMap = new MapWrapper<K, V>();

    this.wrappedMap.forEach((value, key, map) => {
      if (predicate(value, key, map)) {
        filteredMap.set(key, value);
      }
    });

    return filteredMap;
  }
}