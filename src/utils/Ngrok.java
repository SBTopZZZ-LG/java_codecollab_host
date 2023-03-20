package utils;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Proto;
import com.github.alexdlaird.ngrok.protocol.Region;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import io.github.cdimascio.dotenv.Dotenv;

public class Ngrok {
    protected static NgrokClient ngrokClient;
    protected static final CreateTunnel createTunnel = new CreateTunnel.Builder()
            .withProto(Proto.TCP)
            .withAddr(5699)
            .build();
    protected static Tunnel activeTunnel = null;

    protected static final Dotenv dotenv = Dotenv.configure().load();

    public static void init() {
        ngrokClient = new NgrokClient.Builder()
                .withJavaNgrokConfig(new JavaNgrokConfig.Builder()
                        .withRegion(Region.IN)
                        .build())
                .build();
        ngrokClient.setAuthToken(dotenv.get("NGROK_AUTH_TOKEN"));
    }

    public static String createUrl() {
        if (activeTunnel != null)
            return activeTunnel.getPublicUrl();

        activeTunnel = ngrokClient.connect(createTunnel);
        return activeTunnel.getPublicUrl().substring("tcp://".length());
    }
}
