# Save a second, a thousand times

Instead of having to re-open your singleplayer world every time you stop runClient, just have it reopen your world!

![Awesome diagram showing skipping the main menu](https://github.com/cakeGit/AutoLoader/blob/main/img/img.png?raw=true)

During game loading, just **hold F5 and it will skip the autoload process!**

## Getting started

For Neoforge 1.21.1, you can just add the jar to your local mods folder.
However, it can be helpful to add it as a dev dependency, meaning all developers will get it automatically.

First, include the azmod maven (https://maven.azmod.net/):

```gradle
maven {
    name = 'azmod-maven'
    url = "https://maven.azmod.net/releases"
}
```

Then, add the dependency (Versioning is <modversion>+<loader><mcversion>):
```gradle
dependencies {
    //Include the latest 1.0.0 or newer version of autoloader for neoforge 1.21.1, other supported versions may be available, check CF or MR for availability
    implementation("com.cake.autoload:autoload-neoforge-1.21.1:1.+")
}
```

## Configurable

By default, it only opens worlds you were playing on at the time that Minecraft stops, as if you reopened the game where you were either in menus or in a world.
If you saved and closed the world, you will just return to the main screen. Autoloader will let you know if you weren't on a world when you closed the game.

If you just want to always load the world no matter what, then the config option is there.