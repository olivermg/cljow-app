# async components
- mandatorily use mults for incoming channels.
- are topics necessary? we could instead rely on proper core.async infrastrucure externally. i.e.
  external core.async infrastructure must be built accordingly, so components don't have to
  subscribe to topics anymore. this would make components simpler, but building core.async infra-
  structure more complex. however, both things should probably not be intertwined anyway.
- implement waiting for response by passing a promise-chan with request. the fact that this
  is not serializable (i.e. could not be going through e.g. kafka) could be mitigated by
  introduction of "serialization" components that could handle promise-chans and relay responses
  properly.

