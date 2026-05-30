package com.sampple.wifivaultrestore.shizuku;

interface IShizukuShellService {
    void destroy() = 16777114;
    int uid() = 1;
    String run(String command) = 2;
}
