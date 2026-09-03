package com.mycompany.sunrisedentalclinic.controller;

import com.mycompany.sunrisedentalclinic.model.User;
import com.mycompany.sunrisedentalclinic.service.AuthenticationService;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private Label lblMessage;
    private final AuthenticationService auth = new AuthenticationService();

    @FXML
    private void login() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        if (username == null || username.isBlank()) {
            showError("Please enter your username.");
            txtUsername.requestFocus();
            return;
        }
        if (password == null || password.isBlank()) {
            showError("Please enter your password.");
            txtPassword.requestFocus();
            return;
        }

        showError("");
        try {
            User user = auth.login(username, password);
            if (user == null) {
                showError("Incorrect username or password, or this account is inactive.");
                txtPassword.clear();
                txtPassword.requestFocus();
                return;
            }
            FXMLLoader l = new FXMLLoader(getClass().getResource("/com/mycompany/sunrisedentalclinic/view/dashboard.fxml"));
            Parent root = l.load();
            l.<DashboardController>getController().setCurrentUser(user);
            Stage s = (Stage) txtUsername.getScene().getWindow();
            s.setScene(new Scene(root, 1180, 760));
            s.setTitle("Sunrise Dental Clinic - " + user.fullName());
            s.centerOnScreen();
        } catch (Exception e) {
            SQLException sqlError = findCause(e, SQLException.class);
            if (sqlError == null) {
                showError("Unable to sign in: " + safeMessage(e));
            } else if (sqlError.getSQLState() != null && sqlError.getSQLState().startsWith("08")) {
                showError("Cannot connect to MySQL on port 8889. " + safeMessage(sqlError));
            } else {
                showError("Database error: " + safeMessage(sqlError));
            }
        }
    }

    private void showError(String message) {
        lblMessage.setText(message);
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "An unexpected error occurred." : message;
    }
}
