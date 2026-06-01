# WPM+ does not use reflection-heavy app code in the release path.
# Shizuku UserService construction is invoked from Shizuku, so keep the service entrypoint.
-keep class com.sampple.wifivaultrestore.shizuku.ShizukuShellService { *; }
