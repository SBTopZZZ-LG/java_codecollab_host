import utils.Ngrok;
import views.HomeView;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Ngrok.init();

        new HomeView();
    }
}