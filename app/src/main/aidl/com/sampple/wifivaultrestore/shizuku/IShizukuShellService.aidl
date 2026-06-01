package com.sampple.wifivaultrestore.shizuku;

interface IShizukuShellService {
    void destroy() = 16777114;
    int uid() = 1;
    String dumpWifiConfigFiles() = 3;
    String listWifiNetworks() = 4;
}
