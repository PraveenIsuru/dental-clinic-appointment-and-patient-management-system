package com.mycompany.sunrisedentalclinic;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SunriseDentalClinic extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/mycompany/sunrisedentalclinic/view/login.fxml"));
        stage.setScene(new Scene(loader.load(), 900, 560));
        stage.setTitle("Sunrise Dental Clinic");
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
