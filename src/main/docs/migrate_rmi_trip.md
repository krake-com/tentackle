# Migrate Tentackle Application from RMI to TRIP

With the major release 25.18.0.0, the remoting API was refactored to use
the new TRIP protocol instead of RMI. This document describes how to
migrate your application from RMI to TRIP.

### modify the parent pom.xml
Modify the file pattern in the wurbelizer-maven-plugin configuration from `rmi` to `trip`

### Rename folders and modify imports

* All folders with the name `rmi` should be renamed to `trip`
* run the build: the wurblets will overwrite the old code with the new one
* replace in all imports `.rmi.` with `.trip.`

### Replace the rmi templates in the templates folder
Copy `RemoteImplementation.ftl` and `RemoteInterface.ftl` from the tentackle archetype
sources to the templates folder of your application.

### Locate the application's connection implementation
(such as `MyAppConnectionImp.java`), which extends `RemoteDbConnectionImpl`
and modify the constructor from:

```java
public MyAppConnectionImpl(RmiServer server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf)
          throws RemoteException {
```

to:
```java
public MyAppConnectionImpl(TripServer server, Transport transport) {
  super(server, transport);
}
```

and modify `createSession` to:
```java
public RemoteDbSession createSession(SessionInfo clientInfo) {
    return new MyAppRemoteDbSessionImpl(this, clientInfo, getServer().getSessionInfo());
}
```

Note that there are no checked exceptions like `RemoteException` anymore
and remove it from the constructor of the session implementation as well.

### Check replaced DefaultDomainContextFactory, if any
Check that `public DomainContext create()` is overridden.

### Edit the backend.properties
Change the `rmi` protocol to `trip` or `trips`.
