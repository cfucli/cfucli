package cfucli.relay;

import datapotter.datahelper.Data;

@Data
public final class Settings extends Settings_A {
    String redisUrl;
    String restUrl;
    String restToken;
    String transportPreference = TransportPreference.AUTO.name();
    String defaultShell = Platform.defaultShell();
    Integer idleTimeoutMinutes = 15;
    Integer maxSessionHours = 4;
    Integer logRetentionDays = 14;
    Boolean recordOutput = Boolean.TRUE;
    String largeFileExchangeDir;
    Long largeFileThresholdBytes = 256L * 1024;
    Double terminalFontSize = 13.5;
    String appJar;
}
