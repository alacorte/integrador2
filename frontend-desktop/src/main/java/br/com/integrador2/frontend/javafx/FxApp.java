package br.com.integrador2.frontend.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Ponto de entrada de exemplo para a interface JavaFX. Substituir pelas telas
 * reais definidas na spec do frontend.
 *
 * Requer um JDK 8 com JavaFX no runtime (ex: Zulu 8 FX, Liberica Full JDK 8).
 */
public final class FxApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane(new Label("Frontend JavaFX pronto."));
        primaryStage.setTitle("integrador2 - JavaFX");
        primaryStage.setScene(new Scene(root, 400, 200));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
