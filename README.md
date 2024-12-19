## Kelvin
A dynamic and sophisticated gas framework for modern Minecraft.

![kelvin_logo](kelvinicon.png)

In order to include Kelvin in your dev environment, you must first add the Valkyrien Skies maven to your list of repositories:
```gradle
maven {
  url = "https://maven.valkyrienskies.org"
}
```

Then, pull the appropriate Kelvin version from it using the first 10 characters of the commit hash. Kelvin has a build for COMMON, FABRIC, and FORGE environments currently- make sure to use the correct one in your build.gradle(s)!

Common
```gradle
modImplementation("org.valkyrienskies.kelvin:kelvin-common:${minecraft_version}-${kelvin_version}")
```

Fabric
```gradle
modImplementation("org.valkyrienskies.kelvin:kelvin-fabric:${rootProject.minecraft_version}-${rootProject.kelvin_version}")
```

Forge
```gradle
modImplementation("org.valkyrienskies.kelvin:kelvin-forge:${rootProject.minecraft_version}-${rootProject.kelvin_version}")
```


Wiki coming soon!
