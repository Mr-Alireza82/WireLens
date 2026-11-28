# adapters/connection/udp (empty for now)

A UDP relay. It will implement `RelayEngine` from `contexts/relay` and publish the same `TrafficEvent`s as the
TCP adapter, so the UI, filters and exports work without changes.
