package com.mycompany.sunrisedentalclinic.controller;

import com.mycompany.sunrisedentalclinic.SunriseDentalClinic;
import com.mycompany.sunrisedentalclinic.dao.ClinicDAO;
import com.mycompany.sunrisedentalclinic.dao.UserDAO;
import com.mycompany.sunrisedentalclinic.model.Appointment;
import com.mycompany.sunrisedentalclinic.model.Dentist;
import com.mycompany.sunrisedentalclinic.model.Patient;
import com.mycompany.sunrisedentalclinic.model.Treatment;
import com.mycompany.sunrisedentalclinic.model.User;
import com.mycompany.sunrisedentalclinic.service.AppointmentService;
import com.mycompany.sunrisedentalclinic.service.BillingService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.ColumnConstraints;
import javafx.geometry.Insets;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardController {

    // =========================================================
    // HEADER
    // =========================================================
    @FXML
    private Label lblUser;

    @FXML
    private TabPane tabs;

    @FXML
    private TextField globalSearch;

    @FXML
    private Tab tabOverview;

    @FXML
    private Label metricAppointments;

    @FXML
    private Label metricToday;

    @FXML
    private Label metricPatients;

    @FXML
    private TableView<Appointment> overviewTable;

    @FXML
    private Button navOverview, navUsers, navPatients, navAppointments,
            navBilling, navDentists, navTreatments, navHelp, navReports;

    // =========================================================
    // TABS
    // =========================================================
    @FXML
    private Tab tabUsers;

    @FXML
    private Tab tabPatients;

    @FXML
    private Tab tabAppointments;

    @FXML
    private Tab tabBilling;

    @FXML
    private Tab tabTreatments;

    @FXML
    private Tab tabDentists;

    @FXML
    private Tab tabHelp;

    @FXML
    private Tab tabReports;

    // =========================================================
    // USERS
    // =========================================================
    @FXML
    private TableView<User> userTable;

    @FXML
    private TextField uUsername;

    @FXML
    private PasswordField uPassword;

    @FXML
    private TextField uName;

    @FXML
    private TextField uEmail;

    @FXML
    private ComboBox<String> uRole;

    @FXML
    private TextField uDentistSpecialization;

    // =========================================================
    // PATIENTS
    // =========================================================
    @FXML
    private TableView<Patient> patientTable;

    @FXML
    private TextField pName;

    @FXML
    private TextField pAddress;

    @FXML
    private TextField pContact;

    @FXML
    private TextField pEmail;

    @FXML
    private ComboBox<String> pGender;

    // =========================================================
    // APPOINTMENTS
    // =========================================================
    @FXML
    private TableView<Appointment> apptTable;

    @FXML
    private ComboBox<String> aGender;

    @FXML
    private TextField aSearch;

    @FXML
    private TextField aPatientName;

    @FXML
    private TextField aAddress;

    @FXML
    private TextField aContact;

    @FXML
    private TextField aEmail;

    @FXML
    private TextField aTime;

    @FXML
    private TextField aNotes;

    @FXML
    private DatePicker aDate;

    @FXML
    private ComboBox<Dentist> aDentist;

    @FXML
    private ComboBox<Patient> aPatientSearch;

    @FXML
    private ComboBox<Treatment> aTreatment;

    @FXML
    private Label aDentistAvailability;

    @FXML
    private TableView<Dentist> dentistTable;

    @FXML
    private TextField dStartTime, dEndTime;

    @FXML
    private ComboBox<Dentist> dDentist;

    // =========================================================
    // BILLING
    // =========================================================
    @FXML
    private ComboBox<Appointment> bAppointment;

    @FXML
    private TextField bConsultation;

    @FXML
    private VBox receiptPreview;

    @FXML
    private Label previewBillNo;

    @FXML
    private Label previewDate;

    @FXML
    private Label previewPatient;

    @FXML
    private Label previewAppointment;

    @FXML
    private Label previewDentist;

    @FXML
    private Label previewTreatment;

    @FXML
    private Label previewAppointmentDate;

    @FXML
    private Label previewAppointmentTime;

    @FXML
    private Label previewConsultation;

    @FXML
    private Label previewTreatmentCharge;

    @FXML
    private Label previewTotal;

    @FXML
    private Label previewPaymentMethod;

    @FXML
    private ComboBox<String> bPaymentMethod;

    @FXML
    private VBox bCardFields;

    @FXML
    private TextField bCardHolder, bCardNumber, bCardExpiry;

    @FXML
    private PasswordField bCardCvv;

    @FXML
    private Label bTreatmentAmount, bTotalAmount;

    @FXML
    private Button bPrintButton;

    // =========================================================
    // TREATMENTS
    // =========================================================
    @FXML
    private TableView<Treatment> treatmentTable;

    @FXML
    private TextField tName;

    @FXML
    private TextField tDescription;

    @FXML
    private TextField tCost;

    // =========================================================
    // HELP
    // =========================================================
    @FXML
    private ListView<String> helpList;

    @FXML
    private TextField hTitle;

    @FXML
    private TextArea hContent;

    @FXML
    private HBox helpAdminActions;

    // =========================================================
    // REPORTS
    // =========================================================
    @FXML
    private TextArea reportArea;

    @FXML
    private Label reportPatients, reportAppointments, reportBills,
            reportRevenue, reportGeneratedAt;

    private Map<String, Integer> lastReportCounts = Map.of();

    private BigDecimal lastReportRevenue = BigDecimal.ZERO;

    // =========================================================
    // SERVICES / DAO
    // =========================================================
    private User currentUser;

    private final UserDAO users = new UserDAO();

    private final ClinicDAO dao = new ClinicDAO();

    private final AppointmentService appointmentService = new AppointmentService();

    private final BillingService billing = new BillingService();

    private List<String[]> helpRows = List.of();

    private List<Patient> appointmentPatients = List.of();

    // =========================================================
    // INITIALIZE
    // =========================================================
    @FXML
    private void initialize() {

        uRole.setItems(
                FXCollections.observableArrayList(
                        "ADMIN",
                        "RECEPTIONIST",
                        "DENTIST"
                )
        );
        uRole.valueProperty().addListener((observable, oldValue, role) -> {
            boolean dentistRole = "DENTIST".equals(role);
            uDentistSpecialization.setVisible(dentistRole);
            uDentistSpecialization.setManaged(dentistRole);
        });
        uDentistSpecialization.setVisible(false); uDentistSpecialization.setManaged(false);

        pGender.setItems(FXCollections.observableArrayList("Male", "Female", "Other"));
        aGender.setItems(FXCollections.observableArrayList("Male", "Female", "Other"));

        aDate.setValue(LocalDate.now());

        bPaymentMethod.setItems(FXCollections.observableArrayList("CASH", "CARD"));
        bConsultation.textProperty().addListener((observable, oldValue, newValue) -> updateBillingSummary());

        aPatientSearch.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            Patient selected = aPatientSearch.getValue();
            if (selected != null && selected.toString().equals(newValue)) {
                return;
            }
            String query = newValue == null ? "" : newValue.trim().toLowerCase();
            List<Patient> matches = appointmentPatients.stream()
                    .filter(patient -> query.isEmpty()
                    || containsIgnoreCase(patient.fullName(), query)
                    || containsIgnoreCase(patient.contact(), query)
                    || containsIgnoreCase(patient.email(), query))
                    .limit(25)
                    .toList();
            aPatientSearch.setItems(FXCollections.observableArrayList(matches));
            if (!query.isEmpty() && !aPatientSearch.isShowing()) {
                aPatientSearch.show();
            }
        });

        // USER TABLE
        table(
                userTable,
                new String[]{
                    "Username",
                    "Name",
                    "Role",
                    "Active"
                },
                user -> new String[]{
                    user.username(),
                    user.fullName(),
                    user.role(),
                    String.valueOf(user.active())
                }
        );

        userTable.setRowFactory(table -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    editUser();
                }
            });
            return row;
        });

        // PATIENT TABLE
        table(
                patientTable,
                new String[]{
                    "Name",
                    "Gender",
                    "Contact",
                    "Email"
                },
                patient -> new String[]{
                    patient.fullName(),
                    patient.gender(),
                    patient.contact(),
                    patient.email()
                }
        );

        TableColumn<Patient, String> patientNumberColumn = new TableColumn<>("No.");
        patientNumberColumn.setSortable(false);
        patientNumberColumn.setPrefWidth(65);
        patientNumberColumn.setCellValueFactory(data -> new SimpleStringProperty(""));
        patientNumberColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
        patientTable.getColumns().add(0, patientNumberColumn);

        patientTable.setRowFactory(table -> {
            TableRow<Patient> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    viewPatientDetails();
                }
            });
            return row;
        });

        // APPOINTMENT TABLE
        table(
                apptTable,
                new String[]{
                    "Patient",
                    "Dentist",
                    "Treatment",
                    "Date",
                    "Time",
                    "Status",
                    "Payment"
                },
                appointment -> new String[]{
                    appointment.patientName(),
                    appointment.dentistName(),
                    appointment.treatmentName(),
                    String.valueOf(appointment.date()),
                    String.valueOf(appointment.time()),
                    appointment.status(),
                    appointment.paymentStatus()
                }
        );
        addNumberColumn(apptTable);

        apptTable.setRowFactory(table -> {
            TableRow<Appointment> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    editAppointment();
                }
            });
            return row;
        });

        table(
                overviewTable,
                new String[]{
                    "No",
                    "Patient",
                    "Dentist",
                    "Treatment",
                    "Date",
                    "Time",
                    "Status"
                },
                appointment -> new String[]{
                    appointment.appointmentNo(),
                    appointment.patientName(),
                    appointment.dentistName(),
                    appointment.treatmentName(),
                    String.valueOf(appointment.date()),
                    String.valueOf(appointment.time()),
                    appointment.status()
                }
        );

        // TREATMENT TABLE
        table(dentistTable,
                new String[]{"Name", "Specialization", "Phone", "Email", "Start", "End"},
                dentist -> new String[]{dentist.fullName(), dentist.specialization(), dentist.phone(),
                    dentist.email(), dentist.startTime().toString(), dentist.endTime().toString()});
        addNumberColumn(dentistTable);

        // TREATMENT TABLE
        table(
                treatmentTable,
                new String[]{
                    "Treatment",
                    "Description",
                    "Cost"
                },
                treatment -> new String[]{
                    treatment.name(),
                    treatment.description(),
                    String.valueOf(treatment.cost())
                }
        );
        addNumberColumn(treatmentTable);

        treatmentTable.setRowFactory(table -> {
            TableRow<Treatment> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    editTreatment();
                }
            });
            return row;
        });

        // Help selection
        helpList
                .getSelectionModel()
                .selectedIndexProperty()
                .addListener((observable, oldValue, value) -> {

                    int index = value.intValue();

                    if (index >= 0
                            && index < helpRows.size()) {

                        hTitle.setText(
                                helpRows.get(index)[1]
                        );

                        hContent.setText(
                                helpRows.get(index)[2]
                        );
                    }
                });
    }

    // =========================================================
    // LOGGED USER
    // =========================================================
    public void setCurrentUser(User user) {

        this.currentUser = user;

        lblUser.setText(
                user.fullName()
                + " | "
                + user.role()
        );

        configureRoleAccess();

        refreshAll();
    }

    // =========================================================
    // ROLE ACCESS
    // =========================================================
    private void configureRoleAccess() {

        /*
         * Remove all tabs first.
         *
         * We then add only the tabs that the logged-in
         * user is allowed to access.
         */
        tabs.getTabs().clear();

        switch (currentUser.role()) {

            // =================================================
            // ADMIN
            // =================================================
            case "ADMIN" -> {

                tabs.getTabs().addAll(
                        tabOverview,
                        tabUsers,
                        tabPatients,
                        tabAppointments,
                        tabBilling,
                        tabDentists,
                        tabTreatments,
                        tabHelp,
                        tabReports
                );
            }

            // =================================================
            // RECEPTIONIST
            // =================================================
            case "RECEPTIONIST" -> {

                tabs.getTabs().addAll(
                        tabOverview,
                        tabPatients,
                        tabAppointments,
                        tabBilling,
                        tabDentists,
                        tabTreatments,
                        tabHelp
                );
            }

            // =================================================
            // DENTIST
            // =================================================
            case "DENTIST" -> {

                tabs.getTabs().addAll(
                        tabOverview,
                        tabAppointments,
                        tabDentists,
                        tabTreatments,
                        tabHelp
                );
            }

            // =================================================
            // UNKNOWN ROLE
            // =================================================
            default -> {

                tabs.getTabs().add(
                        tabOverview
                );
            }
        }

        boolean admin = "ADMIN".equals(currentUser.role());
        boolean receptionist = "RECEPTIONIST".equals(currentUser.role());
        boolean dentist = "DENTIST".equals(currentUser.role());

        setNavVisible(navOverview, true);
        setNavVisible(navUsers, admin);
        setNavVisible(navPatients, admin || receptionist);
        setNavVisible(navAppointments, admin || receptionist || dentist);
        setNavVisible(navBilling, admin || receptionist);
        setNavVisible(navDentists, admin || receptionist || dentist);
        setNavVisible(navTreatments, admin || receptionist || dentist);
        setNavVisible(navHelp, admin || receptionist || dentist);
        setNavVisible(navReports, admin);
        helpAdminActions.setVisible(admin);
        helpAdminActions.setManaged(admin);
        hTitle.setEditable(admin);
        hContent.setEditable(admin);
        selectTab(tabOverview, navOverview);
    }

    // =========================================================
    // GENERIC TABLE CREATOR
    // =========================================================
    private interface Row<T> {

        String[] values(T item);
    }

    private <T> void table(
            TableView<T> table,
            String[] columnNames,
            Row<T> row
    ) {

        table.getColumns().clear();
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        for (int i = 0;
                i < columnNames.length;
                i++) {

            final int index = i;

            TableColumn<T, String> column
                    = new TableColumn<>(
                            columnNames[i]
                    );

            column.setCellValueFactory(
                    data
                    -> new SimpleStringProperty(
                            row.values(
                                    data.getValue()
                            )[index]
                    )
            );

            column.setPrefWidth(135);

            table.getColumns().add(column);
        }
    }

    private <T> void addNumberColumn(TableView<T> targetTable) {
        TableColumn<T, String> numberColumn = new TableColumn<>("No.");
        numberColumn.setSortable(false);
        numberColumn.setPrefWidth(65);
        numberColumn.setCellValueFactory(data -> new SimpleStringProperty(""));
        numberColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
        targetTable.getColumns().add(0, numberColumn);
    }

    // =========================================================
    // REFRESH ALL
    // =========================================================
    @FXML
    private void refreshAll() {

        try {

            if (currentUser == null) {
                return;
            }

            // ADMIN ONLY USER DATA
            if ("ADMIN".equals(
                    currentUser.role())) {

                userTable.setItems(
                        FXCollections.observableArrayList(
                                users.findAll()
                        )
                );
            }

            // ADMIN + RECEPTIONIST
            if (!"DENTIST".equals(
                    currentUser.role())) {

                appointmentPatients = dao.patients();
                patientTable.setItems(FXCollections.observableArrayList(appointmentPatients));
                aPatientSearch.setItems(FXCollections.observableArrayList(appointmentPatients));
            }

            // DENTISTS
            List<Dentist> dentists = dao.dentists();
            aDentist.setItems(FXCollections.observableArrayList(dentists));
            dDentist.setItems(FXCollections.observableArrayList(dentists));
            dentistTable.setItems(FXCollections.observableArrayList(dentists));

            // TREATMENTS
            List<Treatment> treatments
                    = dao.treatments();

            aTreatment.setItems(
                    FXCollections.observableArrayList(
                            treatments
                    )
            );

            treatmentTable.setItems(
                    FXCollections.observableArrayList(
                            treatments
                    )
            );

            // APPOINTMENTS
            refreshAppointments();

            // HELP
            refreshHelp();

            // ADMIN REPORTS
            if ("ADMIN".equals(
                    currentUser.role())) {

                refreshReports();
            }

        } catch (Exception e) {

            error(e);
        }
    }

    // =========================================================
    // USERS
    // =========================================================
    @FXML
    private void addUser() {

        try {

            requireAdmin();

            required(
                    uUsername,
                    uPassword,
                    uName
            );

            if (uRole.getValue() == null) {

                throw new IllegalArgumentException(
                        "Please select a role."
                );
            }
            if ("DENTIST".equals(uRole.getValue())) {
                required(uDentistSpecialization);
            }

            users.create(
                    uUsername.getText(),
                    uPassword.getText(),
                    uName.getText(),
                    uEmail.getText(),
                    uRole.getValue()
            );
            if ("DENTIST".equals(uRole.getValue())) {
                dao.saveDentistForUser(null, null, uName.getText().trim(), uEmail.getText().trim(),
                        uDentistSpecialization.getText().trim());
            }

            clear(
                    uUsername,
                    uPassword,
                    uName,
                    uEmail,
                    uDentistSpecialization
            );

            refreshAll();

            information(
                    "User created successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void deleteUser() {

        try {

            requireAdmin();

            User selected
                    = userTable
                            .getSelectionModel()
                            .getSelectedItem();

            if (selected == null) {

                throw new IllegalArgumentException(
                        "Please select a user."
                );
            }

            if (selected.userId()
                    == currentUser.userId()) {

                throw new IllegalArgumentException(
                        "You cannot delete your own account."
                );
            }

            users.delete(
                    selected.userId()
            );

            refreshAll();

            information(
                    "User deleted successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void editUser() {
        try {
            requireAdmin();
            User selected = userTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select a user to edit.");
            }

            TextField username = new TextField(selected.username());
            TextField fullName = new TextField(selected.fullName());
            TextField email = new TextField(selected.email());
            PasswordField password = new PasswordField();
            password.setPromptText("Leave blank to keep current password");
            ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList(
                    "ADMIN", "RECEPTIONIST", "DENTIST"));
            role.setValue(selected.role());
            role.setMaxWidth(Double.MAX_VALUE);
            CheckBox active = new CheckBox("Account is active");
            active.setSelected(selected.active());
            Dentist dentistProfile = dao.dentists().stream()
                    .filter(d -> d.fullName().equalsIgnoreCase(selected.fullName())
                    || (selected.email() != null && !selected.email().isBlank()
                    && selected.email().equalsIgnoreCase(d.email())))
                    .findFirst().orElse(null);
            TextField dentistSpecialization = new TextField(
                    dentistProfile == null ? "" : dentistProfile.specialization());

            GridPane form = new GridPane();
            form.setHgap(12);
            form.setVgap(8);
            form.setPadding(new Insets(8, 0, 4, 0));
            form.add(new Label("Username"), 0, 0);
            form.add(username, 0, 1);
            form.add(new Label("Full name"), 1, 0);
            form.add(fullName, 1, 1);
            form.add(new Label("Email address"), 0, 2);
            form.add(email, 0, 3);
            form.add(new Label("Role"), 1, 2);
            form.add(role, 1, 3);
            form.add(new Label("New password"), 0, 4, 2, 1);
            form.add(password, 0, 5, 2, 1);
            Label specializationLabel = new Label("Dentist specialization");
            form.add(specializationLabel, 0, 6, 2, 1);
            form.add(dentistSpecialization, 0, 7, 2, 1);
            form.add(active, 0, 8, 2, 1);
            Runnable updateDentistFields = () -> {
                boolean visible = "DENTIST".equals(role.getValue());
                for (Control control : List.of(specializationLabel, dentistSpecialization)) {
                    control.setVisible(visible); control.setManaged(visible);
                }
            };
            role.valueProperty().addListener((o, oldRole, newRole) -> updateDentistFields.run());
            updateDentistFields.run();
            GridPane.setHgrow(username, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(fullName, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(email, javafx.scene.layout.Priority.ALWAYS);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit user");
            dialog.setHeaderText("Update " + selected.fullName());
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().setPrefWidth(520);
            dialog.getDialogPane().getStyleClass().add("user-edit-dialog");
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

            Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                if (username.getText().isBlank() || fullName.getText().isBlank() || role.getValue() == null) {
                    event.consume();
                    error(new IllegalArgumentException("Username, full name and role are required."));
                }
                if ("DENTIST".equals(role.getValue())) {
                    if (dentistSpecialization.getText().isBlank()) {
                        event.consume();
                        error(new IllegalArgumentException("Dentist specialization is required."));
                    }
                }
            });

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == save) {
                users.update(selected.userId(), username.getText().trim(), password.getText(),
                        fullName.getText().trim(), email.getText().trim(), role.getValue(), active.isSelected());
                if ("DENTIST".equals(role.getValue())) {
                    dao.saveDentistForUser(selected.fullName(), selected.email(), fullName.getText().trim(),
                            email.getText().trim(), dentistSpecialization.getText().trim());
                }
                refreshAll();
                information("User updated successfully.");
            }
        } catch (Exception e) {
            error(e);
        }
    }

    // =========================================================
    // PATIENTS
    // =========================================================
    @FXML
    private void addPatient() {

        try {

            requireReceptionAccess();

            required(
                    pName,
                    pContact
            );

            if (pGender.getValue() == null) {
                throw new IllegalArgumentException("Please select a gender.");
            }

            dao.addPatient(
                    pName.getText(),
                    pGender.getValue(),
                    pAddress.getText(),
                    pContact.getText(),
                    pEmail.getText()
            );

            clear(
                    pName,
                    pAddress,
                    pContact,
                    pEmail
            );
            pGender.setValue(null);

            refreshAll();

            information(
                    "Patient added successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void editPatient() {
        try {
            requireReceptionAccess();
            Patient selected = patientTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select a patient to edit.");
            }

            TextField fullName = new TextField(selected.fullName());
            ComboBox<String> gender = new ComboBox<>(FXCollections.observableArrayList("Male", "Female", "Other"));
            gender.setValue(selected.gender());
            TextField address = new TextField(selected.address());
            TextField contact = new TextField(selected.contact());
            TextField email = new TextField(selected.email());

            GridPane form = new GridPane();
            form.setHgap(12);
            form.setVgap(8);
            form.setPadding(new Insets(8, 0, 4, 0));
            form.add(new Label("Full name"), 0, 0);
            form.add(fullName, 0, 1);
            form.add(new Label("Contact number"), 1, 0);
            form.add(contact, 1, 1);
            form.add(new Label("Gender"), 0, 2);
            form.add(gender, 0, 3);
            form.add(new Label("Address"), 1, 2);
            form.add(address, 1, 3);
            form.add(new Label("Email address"), 0, 4);
            form.add(email, 0, 5, 2, 1);
            GridPane.setHgrow(fullName, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(contact, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(address, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(email, javafx.scene.layout.Priority.ALWAYS);
            gender.setMaxWidth(Double.MAX_VALUE);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit patient");
            dialog.setHeaderText("Update " + selected.fullName());
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().setPrefWidth(520);
            dialog.getDialogPane().getStyleClass().add("user-edit-dialog");
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

            Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                if (fullName.getText().isBlank() || contact.getText().isBlank() || gender.getValue() == null) {
                    event.consume();
                    error(new IllegalArgumentException("Full name, contact number and gender are required."));
                }
            });

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == save) {
                dao.updatePatient(selected.id(), fullName.getText().trim(), gender.getValue(), address.getText().trim(),
                        contact.getText().trim(), email.getText().trim());
                refreshAll();
                information("Patient updated successfully.");
            }
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void viewPatientDetails() {
        try {
            Patient selected = patientTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select a patient to view.");
            }

            List<Appointment> history = dao.appointmentsForPatient(selected.id());

            String initial = selected.fullName() == null || selected.fullName().isBlank()
                    ? "P" : selected.fullName().trim().substring(0, 1).toUpperCase();
            Label avatar = new Label(initial);
            avatar.getStyleClass().add("patient-avatar");

            Label patientName = new Label(selected.fullName());
            patientName.getStyleClass().add("patient-detail-name");
            Label patientMeta = new Label(valueOrDash(selected.gender()) + "  •  "
                    + history.size() + (history.size() == 1 ? " appointment" : " appointments"));
            patientMeta.getStyleClass().add("patient-detail-meta");
            VBox identity = new VBox(3, patientName, patientMeta);

            HBox profileHeader = new HBox(14, avatar, identity);
            profileHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            profileHeader.getStyleClass().add("patient-profile-header");

            GridPane detailGrid = new GridPane();
            detailGrid.setHgap(10);
            detailGrid.setVgap(10);
            ColumnConstraints first = new ColumnConstraints();
            first.setPercentWidth(50);
            ColumnConstraints second = new ColumnConstraints();
            second.setPercentWidth(50);
            detailGrid.getColumnConstraints().addAll(first, second);
            detailGrid.add(patientDetailField("CONTACT", selected.contact()), 0, 0);
            detailGrid.add(patientDetailField("EMAIL", selected.email()), 1, 0);
            detailGrid.add(patientDetailField("ADDRESS", selected.address()), 0, 1, 2, 1);

            TableView<Appointment> historyTable = new TableView<>();
            table(historyTable,
                    new String[]{"Appointment No.", "Dentist", "Treatment", "Date", "Time", "Status", "Payment", "Notes"},
                    appointment -> new String[]{
                        appointment.appointmentNo(), appointment.dentistName(), appointment.treatmentName(),
                        String.valueOf(appointment.date()), String.valueOf(appointment.time()),
                        appointment.status(), appointment.paymentStatus(), valueOrDash(appointment.notes())
                    });
            historyTable.setItems(FXCollections.observableArrayList(history));
            historyTable.setPrefHeight(300);
            historyTable.setPlaceholder(new Label("No previous appointments found."));
            historyTable.getStyleClass().add("patient-history-table");

            Label historyTitle = new Label("Appointment history");
            historyTitle.getStyleClass().add("patient-history-title");
            Label historyHint = new Label("Most recent appointments appear first");
            historyHint.getStyleClass().add("patient-detail-meta");
            VBox historyHeading = new VBox(2, historyTitle, historyHint);

            VBox content = new VBox(16, profileHeader, detailGrid, historyHeading, historyTable);
            content.getStyleClass().add("patient-details-content");
            content.setPrefWidth(900);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Patient details");
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().getStyleClass().add("patient-details-dialog");
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            dialog.setResizable(true);
            dialog.showAndWait();
        } catch (Exception e) {
            error(e);
        }
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private VBox patientDetailField(String label, String value) {
        Label caption = new Label(label);
        caption.getStyleClass().add("patient-field-caption");
        Label content = new Label(valueOrDash(value));
        content.setWrapText(true);
        content.getStyleClass().add("patient-field-value");
        VBox field = new VBox(5, caption, content);
        field.getStyleClass().add("patient-field-card");
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
        return field;
    }

    @FXML
    private void deletePatient() {
        try {
            requireReceptionAccess();
            Patient selected = patientTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select a patient to delete.");
            }

            int appointmentCount = dao.patientAppointmentCount(selected.id());
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete patient");
            confirmation.setHeaderText("Delete " + selected.fullName() + "?");
            confirmation.setContentText(appointmentCount == 0
                    ? "This patient record will be permanently deleted."
                    : "This will also permanently delete " + appointmentCount
                            + " linked appointment(s) and their billing records.");
            ButtonType delete = new ButtonType("Delete patient", ButtonBar.ButtonData.OK_DONE);
            confirmation.getButtonTypes().setAll(delete, ButtonType.CANCEL);
            confirmation.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != delete) {
                return;
            }

            dao.deletePatient(selected.id());
            refreshAll();
            information("Patient deleted successfully.");
        } catch (Exception e) {
            error(e);
        }
    }

    // =========================================================
    // APPOINTMENTS
    // =========================================================
    private static boolean containsIgnoreCase(String value, String lowerCaseQuery) {
        return value != null && value.toLowerCase().contains(lowerCaseQuery);
    }

    @FXML
    private void selectAppointmentPatient() {
        Patient patient = aPatientSearch.getValue();
        if (patient == null) {
            return;
        }
        aPatientName.setText(patient.fullName());
        aAddress.setText(patient.address());
        aContact.setText(patient.contact());
        aEmail.setText(patient.email());
        aGender.setValue(patient.gender());
    }

    @FXML
    private void showDentistAvailability() {
        Dentist dentist = aDentist.getValue();
        aDentistAvailability.setText(dentist == null
                ? "Select a dentist to see available hours"
                : "Available today: " + dentist.availability()
                    + "  •  Choose an appointment time within this range");
    }

    @FXML
    private void saveDentistAvailability() {
        try {
            requireReceptionAccess();
            required(dStartTime, dEndTime);
            if (dDentist.getValue() == null) {
                throw new IllegalArgumentException("Please select a registered dentist.");
            }
            LocalTime start = LocalTime.parse(dStartTime.getText().trim());
            LocalTime end = LocalTime.parse(dEndTime.getText().trim());
            validateDentistHours(start, end);
            dao.updateDentistAvailability(dDentist.getValue().id(), start, end);
            clear(dStartTime, dEndTime);
            dDentist.setValue(null);
            refreshAll();
            information("Dentist availability saved successfully.");
        } catch (Exception e) {
            error(e instanceof java.time.format.DateTimeParseException
                    ? new IllegalArgumentException("Dentist times must use HH:mm format. Example: 09:00") : e);
        }
    }

    @FXML
    private void editDentist() {
        try {
            requireReceptionAccess();
            Dentist selected = dentistTable.getSelectionModel().getSelectedItem();
            if (selected == null) throw new IllegalArgumentException("Please select a dentist to edit.");
            TextField start = new TextField(selected.startTime().toString());
            TextField end = new TextField(selected.endTime().toString());
            GridPane form = new GridPane();
            form.setHgap(10); form.setVgap(8); form.setPadding(new Insets(8));
            form.addRow(0, new Label("Start time (HH:mm)"), start);
            form.addRow(1, new Label("End time (HH:mm)"), end);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit dentist"); dialog.setHeaderText(selected.fullName());
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == save) {
                LocalTime startTime = LocalTime.parse(start.getText().trim());
                LocalTime endTime = LocalTime.parse(end.getText().trim());
                validateDentistHours(startTime, endTime);
                dao.updateDentistAvailability(selected.id(), startTime, endTime);
                refreshAll();
                information("Dentist updated successfully.");
            }
        } catch (Exception e) {
            error(e instanceof java.time.format.DateTimeParseException
                    ? new IllegalArgumentException("Dentist times must use HH:mm format. Example: 09:00") : e);
        }
    }

    private void validateDentistHours(LocalTime start, LocalTime end) {
        appointmentService.validateDentistHours(start, end);
    }

    @FXML
    private void registerAppointment() {

        try {

            /*
             * Dentists can VIEW appointments,
             * but cannot create appointments.
             */
            requireReceptionAccess();

            required(
                    aPatientName,
                    aContact,
                    aTime
            );

            if (aDentist.getValue() == null) {

                throw new IllegalArgumentException(
                        "Please select a dentist."
                );
            }

            if (aTreatment.getValue() == null) {

                throw new IllegalArgumentException(
                        "Please select a treatment."
                );
            }

            if (aDate.getValue() == null) {

                throw new IllegalArgumentException(
                        "Please select a date."
                );
            }
            if (aGender.getValue() == null) {
                throw new IllegalArgumentException("Please select the patient's gender.");
            }

            LocalTime time = appointmentService.parseTime(aTime.getText());

            boolean available
                    = dao.slotAvailable(
                            aDentist.getValue().id(),
                            aDate.getValue(),
                            time
                    );

            appointmentService.validateSlot(
                    aDentist.getValue(), aDate.getValue(), time, available);

            java.util.Optional<Patient> existingPatient
                    = dao.findPatientByContactOrEmail(
                            aContact.getText(),
                            aEmail.getText()
                    );

            boolean newPatient = existingPatient.isEmpty();
            int patientId;
            if (existingPatient.isPresent()) {
                patientId = existingPatient.get().id();
            } else {
                patientId = dao.addPatient(
                        aPatientName.getText().trim(),
                        aGender.getValue(),
                        aAddress.getText().trim(),
                        aContact.getText().trim(),
                        aEmail.getText().trim()
                );
            }

            dao.addAppointment(
                    dao.nextAppointmentNumber(),
                    patientId,
                    aDentist.getValue().id(),
                    aTreatment.getValue().id(),
                    aDate.getValue(),
                    time,
                    aNotes.getText()
            );

            clear(
                    aPatientName,
                    aAddress,
                    aContact,
                    aEmail,
                    aTime,
                    aNotes
            );
            aPatientSearch.setValue(null);
            aGender.setValue(null);
            aPatientSearch.getEditor().clear();
            aPatientSearch.setItems(FXCollections.observableArrayList(appointmentPatients));

            refreshAll();

            information(newPatient
                    ? "Appointment created and new patient registered successfully."
                    : "Appointment created for the existing patient successfully.");

        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void editAppointment() {
        try {
            requireReceptionAccess();
            Appointment selected = apptTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select an appointment to edit.");
            }

            TextField number = new TextField(selected.appointmentNo());
            ComboBox<Dentist> dentist = new ComboBox<>(FXCollections.observableArrayList(dao.dentists()));
            dentist.getItems().stream().filter(item -> item.id() == selected.dentistId())
                    .findFirst().ifPresent(dentist::setValue);
            dentist.setMaxWidth(Double.MAX_VALUE);
            ComboBox<Treatment> treatment = new ComboBox<>(FXCollections.observableArrayList(dao.treatments()));
            treatment.getItems().stream().filter(item -> item.id() == selected.treatmentId())
                    .findFirst().ifPresent(treatment::setValue);
            treatment.setMaxWidth(Double.MAX_VALUE);
            DatePicker date = new DatePicker(selected.date());
            date.setMaxWidth(Double.MAX_VALUE);
            TextField time = new TextField(selected.time().toString());
            ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList(
                    "BOOKED", "COMPLETED", "CANCELLED"));
            status.setValue(selected.status());
            status.setMaxWidth(Double.MAX_VALUE);
            TextArea notes = new TextArea(selected.notes());
            notes.setPromptText("Appointment notes");
            notes.setPrefRowCount(2);
            notes.setWrapText(true);

            GridPane form = new GridPane();
            form.setHgap(12);
            form.setVgap(8);
            form.setPadding(new Insets(8, 0, 4, 0));
            form.add(new Label("Patient"), 0, 0);
            form.add(new Label(selected.patientName()), 0, 1);
            form.add(new Label("Appointment number"), 1, 0);
            form.add(number, 1, 1);
            form.add(new Label("Dentist"), 0, 2);
            form.add(dentist, 0, 3);
            form.add(new Label("Treatment"), 1, 2);
            form.add(treatment, 1, 3);
            form.add(new Label("Date"), 0, 4);
            form.add(date, 0, 5);
            form.add(new Label("Time (HH:mm)"), 1, 4);
            form.add(time, 1, 5);
            form.add(new Label("Status"), 0, 6);
            form.add(status, 0, 7);
            form.add(new Label("Notes"), 0, 8, 2, 1);
            form.add(notes, 0, 9, 2, 1);
            GridPane.setHgrow(number, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(dentist, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(treatment, javafx.scene.layout.Priority.ALWAYS);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit appointment");
            dialog.setHeaderText("Update appointment for " + selected.patientName());
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().setPrefWidth(560);
            dialog.getDialogPane().getStyleClass().add("user-edit-dialog");
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

            final LocalTime[] parsedTime = new LocalTime[1];
            Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    if (number.getText().isBlank() || dentist.getValue() == null
                            || treatment.getValue() == null || date.getValue() == null
                            || status.getValue() == null) {
                        throw new IllegalArgumentException("Please complete all appointment fields.");
                    }
                    appointmentService.validateAppointmentNumber(number.getText());
                    parsedTime[0] = appointmentService.parseTime(time.getText());
                    boolean available = dao.slotAvailableForUpdate(selected.id(), dentist.getValue().id(),
                            date.getValue(), parsedTime[0]);
                    appointmentService.validateSlot(
                            dentist.getValue(), date.getValue(), parsedTime[0], available);
                } catch (Exception e) {
                    event.consume();
                    error(e instanceof IllegalArgumentException
                            ? (IllegalArgumentException) e
                            : new IllegalArgumentException("Time must use HH:mm format. Example: 14:30"));
                }
            });

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == save) {
                dao.changeAppointmentNumber(selected.id(), selected.appointmentNo(), number.getText().trim());
                dao.updateAppointment(selected.id(), number.getText().trim(), dentist.getValue().id(),
                        treatment.getValue().id(), date.getValue(), parsedTime[0], status.getValue(),
                        notes.getText().trim());
                refreshAll();
                information("Appointment updated successfully.");
            }
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void refreshAppointments() {

        try {

            String search = "";

            if (aSearch != null) {

                search = aSearch.getText();
            }

            List<Appointment> appointments
                    = dao.appointments(search);
            appointments.sort((left, right) -> {
                int numericOrder = Long.compare(appointmentOrder(left.appointmentNo()),
                        appointmentOrder(right.appointmentNo()));
                return numericOrder != 0 ? numericOrder
                        : left.appointmentNo().compareToIgnoreCase(right.appointmentNo());
            });

            apptTable.setItems(
                    FXCollections.observableArrayList(
                            appointments
                    )
            );

            updateOverview(appointments);


            /*
             * Billing is only available for
             * Admin / Receptionist.
             */
            if (!"DENTIST".equals(
                    currentUser.role())) {

                bAppointment.setItems(
                        FXCollections.observableArrayList(
                                dao.unpaidAppointments()
                        )
                );
            }

        } catch (Exception e) {

            error(e);
        }
    }

    // =========================================================
    // DASHBOARD OVERVIEW / NAVIGATION SEARCH
    // =========================================================
    private void updateOverview(List<Appointment> appointments) {

        metricAppointments.setText(String.valueOf(appointments.size()));
        metricToday.setText(String.valueOf(
                appointments.stream()
                        .filter(a -> LocalDate.now().equals(a.date()))
                        .count()
        ));
        metricPatients.setText(patientTable == null
                ? "0"
                : String.valueOf(patientTable.getItems().size()));

        overviewTable.setItems(FXCollections.observableArrayList(
                appointments.stream().limit(8).toList()
        ));
    }

    private long appointmentOrder(String appointmentNumber) {
        if (appointmentNumber == null) {
            return Long.MAX_VALUE;
        }
        String digits = appointmentNumber.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    @FXML
    private void searchNavigation() {

        String query = globalSearch.getText() == null
                ? ""
                : globalSearch.getText().trim().toLowerCase();

        if (query.isEmpty()) {
            selectTab(tabOverview, navOverview);
            return;
        }

        tabs.getTabs().stream()
                .filter(tab -> tab.getText().toLowerCase().contains(query))
                .findFirst()
                .ifPresentOrElse(
                        tab -> selectTab(tab, navButtonFor(tab)),
                        () -> globalSearch.setPromptText("No matching section")
                );
    }

    private Button navButtonFor(Tab tab) {
        if (tab == tabUsers) {
            return navUsers;
        }
        if (tab == tabPatients) {
            return navPatients;
        }
        if (tab == tabAppointments) {
            return navAppointments;
        }
        if (tab == tabBilling) {
            return navBilling;
        }
        if (tab == tabDentists) {
            return navDentists;
        }
        if (tab == tabTreatments) {
            return navTreatments;
        }
        if (tab == tabHelp) {
            return navHelp;
        }
        if (tab == tabReports) {
            return navReports;
        }
        return navOverview;
    }

    private void setNavVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void selectTab(Tab tab, Button selectedButton) {
        if (!tabs.getTabs().contains(tab)) {
            return;
        }

        tabs.getSelectionModel().select(tab);
        for (Button button : List.of(
                navOverview, navUsers, navPatients, navAppointments,
                navBilling, navDentists, navTreatments, navHelp, navReports)) {
            button.getStyleClass().remove("selected");
        }
        selectedButton.getStyleClass().add("selected");
    }

    @FXML
    private void showOverview() {
        selectTab(tabOverview, navOverview);
    }

    @FXML
    private void showUsers() {
        selectTab(tabUsers, navUsers);
    }

    @FXML
    private void showPatients() {
        selectTab(tabPatients, navPatients);
    }

    @FXML
    private void showAppointments() {
        selectTab(tabAppointments, navAppointments);
    }

    @FXML
    private void showBilling() {
        selectTab(tabBilling, navBilling);
    }

    @FXML
    private void showDentists() {
        selectTab(tabDentists, navDentists);
    }

    @FXML
    private void showTreatments() {
        selectTab(tabTreatments, navTreatments);
    }


    @FXML
    private void showHelp() {
        selectTab(tabHelp, navHelp);
    }

    @FXML
    private void showReports() {
        selectTab(tabReports, navReports);
    }

    @FXML
    private void deleteAppointment() {

        try {

            requireReceptionAccess();

            Appointment appointment
                    = apptTable
                            .getSelectionModel()
                            .getSelectedItem();

            if (appointment == null) {

                throw new IllegalArgumentException(
                        "Please select an appointment."
                );
            }

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete appointment");
            confirmation.setHeaderText("Delete appointment " + appointment.appointmentNo() + "?");
            confirmation.setContentText("Any billing record linked to this appointment will also be permanently deleted.");
            ButtonType delete = new ButtonType("Delete appointment", ButtonBar.ButtonData.OK_DONE);
            confirmation.getButtonTypes().setAll(delete, ButtonType.CANCEL);
            confirmation.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != delete) {
                return;
            }

            dao.deleteAppointment(
                    appointment.id()
            );

            refreshAll();

            information(
                    "Appointment deleted successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    // =========================================================
    // BILLING
    // =========================================================
    @FXML
    private void processPayment() {
        try {
            requireReceptionAccess();
            Appointment appointment = bAppointment.getValue();
            if (appointment == null) {
                throw new IllegalArgumentException("Please select an appointment.");
            }
            if (bPaymentMethod.getValue() == null) {
                throw new IllegalArgumentException("Please select cash or card payment.");
            }

            BigDecimal consultation;
            try {
                consultation = new BigDecimal(bConsultation.getText().trim());
                if (consultation.signum() < 0) {
                    throw new NumberFormatException();
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Enter a valid consultation fee.");
            }

            String paymentMethod = bPaymentMethod.getValue();
            String cardLast4 = null;
            if ("CARD".equals(paymentMethod)) {
                String digits = bCardNumber.getText() == null
                        ? "" : bCardNumber.getText().replaceAll("\\D", "");
                cardLast4 = digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
            }

            BigDecimal treatmentCharge = dao.treatmentCost(appointment.treatmentId());
            BigDecimal total = billing.calculateTotal(consultation, treatmentCharge);
            int billId = dao.saveBill(appointment.id(), consultation, treatmentCharge,
                    total, paymentMethod, cardLast4);

            previewBillNo.setText(
                    "BILL-" + String.format("%04d", billId)
            );

            previewDate.setText(
                    LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern(
                                    "dd MMM yyyy, HH:mm"
                            )
                    )
            );

            previewPatient.setText(
                    appointment.patientName()
            );

            previewAppointment.setText(
                    appointment.appointmentNo()
            );

            previewDentist.setText(
                    appointment.dentistName()
            );

            previewTreatment.setText(
                    appointment.treatmentName()
            );

            previewAppointmentDate.setText(
                    String.valueOf(appointment.date())
            );

            previewAppointmentTime.setText(
                    String.valueOf(appointment.time())
            );

            previewConsultation.setText(
                    money(consultation)
            );

            previewTreatmentCharge.setText(
                    money(treatmentCharge)
            );

            previewTotal.setText(
                    money(total)
            );

            if ("CARD".equals(paymentMethod)) {

                previewPaymentMethod.setText(
                        cardLast4 == null || cardLast4.isBlank()
                                ? "Card"
                                : "Card •••• " + cardLast4
                );

            } else {

                previewPaymentMethod.setText(
                        "Cash"
                );
            }
            createReceiptPdf(billId, appointment, consultation,
                    treatmentCharge, total, paymentMethod, cardLast4);
            bPrintButton.setDisable(false);
            bAppointment.setValue(null);
            bAppointment.setItems(FXCollections.observableArrayList(dao.unpaidAppointments()));
            bConsultation.clear();
            bPaymentMethod.setValue(null);
            bCardFields.setVisible(false);
            bCardFields.setManaged(false);
            refreshAppointments();

            if ("ADMIN".equals(currentUser.role())) {
                refreshReports();
            }

            information("CARD".equals(paymentMethod)
                    ? "Card payment successful. Receipt PDF created."
                    : "Cash payment recorded successfully. Receipt PDF created.");
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void updateBillingMethod() {
        boolean card = "CARD".equals(bPaymentMethod.getValue());
        bCardFields.setVisible(card);
        bCardFields.setManaged(card);
    }

    @FXML
    private void updateBillingSummary() {
        try {
            Appointment appointment = bAppointment.getValue();
            BigDecimal treatment = appointment == null
                    ? BigDecimal.ZERO : dao.treatmentCost(appointment.treatmentId());
            BigDecimal consultation;
            try {
                consultation = new BigDecimal(bConsultation.getText().trim());
            } catch (Exception ignored) {
                consultation = BigDecimal.ZERO;
            }
            bTreatmentAmount.setText(money(treatment));
            bTotalAmount.setText(money(billing.calculateTotal(consultation, treatment)));
        } catch (Exception e) {
            bTreatmentAmount.setText("Rs. 0.00");
            bTotalAmount.setText("Rs. 0.00");
        }
    }

    private String buildReceiptText(int billId, Appointment appointment, BigDecimal consultation,
            BigDecimal treatment, BigDecimal total, String method, String cardLast4) {
        String payment = "CARD".equals(method) && cardLast4 != null && !cardLast4.isBlank()
                ? "CARD ending " + cardLast4 : method;
        return "SUNRISE DENTAL CLINIC\n"
                + "PAYMENT RECEIPT\n"
                + "========================================\n"
                + "Bill number       BILL-" + String.format("%04d", billId) + "\n"
                + "Appointment       " + appointment.appointmentNo() + "\n"
                + "Patient           " + appointment.patientName() + "\n"
                + "Dentist           " + appointment.dentistName() + "\n"
                + "Treatment         " + appointment.treatmentName() + "\n"
                + "Date / time       " + appointment.date() + "  " + appointment.time() + "\n"
                + "Payment method    " + payment + "\n"
                + "----------------------------------------\n"
                + String.format("%-25s Rs. %,.2f%n", "Consultation fee", consultation)
                + String.format("%-25s Rs. %,.2f%n", "Treatment charge", treatment)
                + "----------------------------------------\n"
                + String.format("%-25s Rs. %,.2f%n", "TOTAL PAID", total)
                + "========================================\n"
                + "Payment status: PAID\n"
                + "Thank you for choosing Sunrise Dental Clinic.";
    }

    private static String money(BigDecimal amount) {
        return String.format("Rs. %,.2f", amount);
    }

    private Path createReceiptPdf(int billId, Appointment appointment, BigDecimal consultation,
            BigDecimal treatment, BigDecimal total, String method, String cardLast4) throws IOException {
        Path directory = Paths.get("receipts").toAbsolutePath();
        Files.createDirectories(directory);
        Path output = directory.resolve("receipt-BILL-" + String.format("%04d", billId) + ".pdf");

        String payment = "CARD".equals(method) && cardLast4 != null && !cardLast4.isBlank()
                ? "CARD ending " + cardLast4 : method;
        StringBuilder content = new StringBuilder();
        content.append("0.024 0.169 0.302 rg 0 495 420 100 re f\n");
        content.append(pdfText(32, 548, 19, true, "SUNRISE DENTAL CLINIC", true));
        content.append(pdfText(32, 528, 9, false, "PAYMENT RECEIPT", true));
        content.append("0.059 0.09 0.165 rg\n");
        content.append(pdfText(32, 463, 11, true, "Receipt details", false));
        content.append("0.886 0.91 0.941 RG 32 452 m 388 452 l S\n");

        int y = 425;
        y = addPdfDetail(content, y, "Bill number", "BILL-" + String.format("%04d", billId));
        y = addPdfDetail(content, y, "Appointment", appointment.appointmentNo());
        y = addPdfDetail(content, y, "Patient", appointment.patientName());
        y = addPdfDetail(content, y, "Dentist", appointment.dentistName());
        y = addPdfDetail(content, y, "Treatment", appointment.treatmentName());
        y = addPdfDetail(content, y, "Date / time", appointment.date() + "  " + appointment.time());
        y = addPdfDetail(content, y, "Payment method", payment);
        addPdfDetail(content, y, "Paid at", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        content.append("0.973 0.98 0.988 rg 32 90 356 96 re f\n");
        content.append("0.278 0.333 0.412 rg\n");
        content.append(pdfText(46, 158, 9, false, "Consultation fee", false));
        content.append(pdfText(270, 158, 9, true, money(consultation), false));
        content.append(pdfText(46, 137, 9, false, "Treatment charge", false));
        content.append(pdfText(270, 137, 9, true, money(treatment), false));
        content.append("0.059 0.463 0.431 rg\n");
        content.append(pdfText(46, 108, 12, true, "TOTAL PAID", false));
        content.append(pdfText(270, 108, 12, true, money(total), false));
        content.append("0.392 0.455 0.545 rg\n");
        content.append(pdfText(116, 54, 8, false, "Thank you for choosing Sunrise Dental Clinic", false));
        content.append(pdfText(132, 40, 8, false, "This receipt was generated electronically.", false));

        writeSimplePdf(output, content.toString());
        return output;
    }

    private int addPdfDetail(StringBuilder content, int y, String label, String value) {
        content.append("0.392 0.455 0.545 rg\n");
        content.append(pdfText(32, y, 9, false, label, false));
        content.append("0.059 0.09 0.165 rg\n");
        String safeValue = value == null ? "" : value;
        if (safeValue.length() > 42) {
            safeValue = safeValue.substring(0, 39) + "...";
        }
        content.append(pdfText(205, y, 9, true, safeValue, false));
        return y - 24;
    }

    private String pdfText(int x, int y, int size, boolean bold, String value, boolean white) {
        String escaped = (value == null ? "" : value)
                .replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
        String color = white ? "1 1 1 rg\n" : "";
        return color + "BT /" + (bold ? "F2" : "F1") + " " + size + " Tf "
                + x + " " + y + " Td (" + escaped + ") Tj ET\n";
    }

    private void writeSimplePdf(Path output, String pageContent) throws IOException {
        writeSimplePdf(output, pageContent, 420, 595);
    }

    private void writeSimplePdf(Path output, String pageContent, int width, int height) throws IOException {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> offsets = new ArrayList<>();
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + width + " " + height + "] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
            "<< /Length " + pageContent.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n"
                    + pageContent + "endstream"
        };
        for (int i = 0; i < objects.length; i++) {
            offsets.add(pdf.size());
            pdf.write(((i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = pdf.size();
        pdf.write(("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n")
                .getBytes(StandardCharsets.ISO_8859_1));
        for (int offset : offsets) {
            pdf.write(String.format(Locale.US, "%010d 00000 n \n", offset)
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        pdf.write(("trailer\n<< /Size " + (objects.length + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));
        Files.write(output, pdf.toByteArray());
    }

    private boolean openPdf(Path pdf) {
        try {
            if (pdf != null && Files.exists(pdf) && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(pdf.toFile());
                return true;
            }
        } catch (Exception ignored) {
            // The receipt remains available through the Open PDF button.
        }
        return false;
    }

    @FXML
    private void printReceipt() {

        try {

            requireReceptionAccess();

            if (previewBillNo.getText() == null
                    || "-".equals(previewBillNo.getText())) {

                throw new IllegalArgumentException(
                        "Complete a payment first."
                );
            }

            PrinterJob job = PrinterJob.createPrinterJob();

            if (job == null) {

                throw new IllegalStateException(
                        "Printer is not available."
                );
            }

            boolean print = job.showPrintDialog(
                    receiptPreview
                            .getScene()
                            .getWindow()
            );

            if (print) {

                boolean success = job.printPage(
                        receiptPreview
                );

                if (success) {

                    job.endJob();

                    information(
                            "Receipt sent to printer."
                    );
                }
            }

        } catch (Exception e) {

            error(e);
        }
    }

    // =========================================================
    // TREATMENTS
    // =========================================================
    @FXML
    private void addTreatment() {

        try {

            /*
             * Only Admin can modify treatment prices.
             */
            requireAdmin();

            required(
                    tName,
                    tCost
            );

            BigDecimal cost;

            try {

                cost = new BigDecimal(
                        tCost
                                .getText()
                                .trim()
                );

            } catch (NumberFormatException e) {

                throw new IllegalArgumentException(
                        "Treatment cost must be a number."
                );
            }

            if (cost.signum() < 0) {
                throw new IllegalArgumentException("Treatment cost cannot be negative.");
            }

            dao.addTreatment(
                    tName.getText(),
                    tDescription.getText(),
                    cost
            );

            clear(
                    tName,
                    tDescription,
                    tCost
            );

            refreshAll();

            information(
                    "Treatment added successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void editTreatment() {
        try {
            requireAdmin();
            Treatment selected = treatmentTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select a treatment to edit.");
            }

            TextField name = new TextField(selected.name());
            TextArea description = new TextArea(selected.description());
            description.setPrefRowCount(3);
            description.setWrapText(true);
            TextField cost = new TextField(selected.cost().toPlainString());

            GridPane form = new GridPane();
            form.setHgap(12);
            form.setVgap(8);
            form.setPadding(new Insets(8, 0, 4, 0));
            form.add(new Label("Treatment name"), 0, 0);
            form.add(name, 0, 1);
            form.add(new Label("Cost (Rs.)"), 1, 0);
            form.add(cost, 1, 1);
            form.add(new Label("Description"), 0, 2, 2, 1);
            form.add(description, 0, 3, 2, 1);
            GridPane.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
            GridPane.setHgrow(cost, javafx.scene.layout.Priority.ALWAYS);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Edit treatment");
            dialog.setHeaderText("Update " + selected.name());
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().setPrefWidth(520);
            dialog.getDialogPane().getStyleClass().add("user-edit-dialog");
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

            final BigDecimal[] parsedCost = new BigDecimal[1];
            Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    if (name.getText().isBlank()) {
                        throw new IllegalArgumentException("Treatment name is required.");
                    }
                    parsedCost[0] = new BigDecimal(cost.getText().trim());
                    if (parsedCost[0].signum() < 0) {
                        throw new IllegalArgumentException("Treatment cost cannot be negative.");
                    }
                } catch (NumberFormatException e) {
                    event.consume();
                    error(new IllegalArgumentException("Treatment cost must be a number."));
                } catch (IllegalArgumentException e) {
                    event.consume();
                    error(e);
                }
            });

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == save) {
                dao.updateTreatment(selected.id(), name.getText().trim(),
                        description.getText().trim(), parsedCost[0]);
                refreshAll();
                information("Treatment updated successfully.");
            }
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void deleteTreatment() {
        try {
            requireAdmin();
            Treatment selected = treatmentTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Please select a treatment to delete.");
            }
            int appointmentCount = dao.treatmentAppointmentCount(selected.id());
            if (appointmentCount > 0) {
                throw new IllegalArgumentException("This treatment is used by " + appointmentCount
                        + " appointment(s) and cannot be deleted. You can edit it instead.");
            }

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete treatment");
            confirmation.setHeaderText("Delete " + selected.name() + "?");
            confirmation.setContentText("This treatment will be permanently deleted.");
            ButtonType delete = new ButtonType("Delete treatment", ButtonBar.ButtonData.OK_DONE);
            confirmation.getButtonTypes().setAll(delete, ButtonType.CANCEL);
            confirmation.getDialogPane().getStylesheets().add(
                    getClass().getResource("/com/mycompany/sunrisedentalclinic/view/clinic.css").toExternalForm());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != delete) {
                return;
            }

            dao.deleteTreatment(selected.id());
            refreshAll();
            information("Treatment deleted successfully.");
        } catch (Exception e) {
            error(e);
        }
    }

    // =========================================================
    // HELP
    // =========================================================
    @FXML
    private void refreshHelp() {

        try {

            helpRows
                    = dao.helpTopics();

            List<String> titles
                    = new ArrayList<>();

            for (String[] row : helpRows) {

                titles.add(
                        row[1]
                );
            }

            helpList.setItems(
                    FXCollections.observableArrayList(
                            titles
                    )
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void addHelp() {

        try {

            requireAdmin();

            required(
                    hTitle
            );

            if (hContent
                    .getText()
                    .isBlank()) {

                throw new IllegalArgumentException(
                        "Help content is required."
                );
            }

            String title = hTitle.getText().trim();
            String content = hContent.getText().trim();
            if (dao.helpTitleExists(title)) {
                throw new IllegalArgumentException(
                        "A help topic with this title already exists. Choose New topic or use another title.");
            }
            dao.addHelp(title, content);

            hTitle.clear();
            hContent.clear();

            refreshHelp();

            information(
                    "Help topic added successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void newHelpTopic() {
        try {
            requireAdmin();
            helpList.getSelectionModel().clearSelection();
            hTitle.clear();
            hContent.clear();
            hTitle.requestFocus();
        } catch (Exception e) {
            error(e);
        }
    }

    @FXML
    private void deleteHelp() {

        try {

            requireAdmin();

            int index
                    = helpList
                            .getSelectionModel()
                            .getSelectedIndex();

            if (index < 0) {

                throw new IllegalArgumentException(
                        "Please select a help topic."
                );
            }

            int topicId
                    = Integer.parseInt(
                            helpRows
                                    .get(index)[0]
                    );

            dao.deleteHelp(
                    topicId
            );

            hTitle.clear();
            hContent.clear();

            refreshHelp();

            information(
                    "Help topic deleted successfully."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    // =========================================================
    // REPORTS
    // =========================================================
    @FXML
    private void refreshReports() {

        try {

            if (currentUser == null) {
                return;
            }

            if (!"ADMIN".equals(
                    currentUser.role())) {

                return;
            }

            lastReportCounts = dao.reportCounts();
            lastReportRevenue = dao.totalRevenue();
            String generatedAt = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            reportPatients.setText(String.valueOf(lastReportCounts.getOrDefault("Patients", 0)));
            reportAppointments.setText(String.valueOf(lastReportCounts.getOrDefault("Appointments", 0)));
            reportBills.setText(String.valueOf(lastReportCounts.getOrDefault("Bills", 0)));
            reportRevenue.setText(money(lastReportRevenue));
            reportGeneratedAt.setText(generatedAt);

            reportArea.setText(
                    "SUNRISE DENTAL CLINIC\n"
                    + "SYSTEM SUMMARY REPORT\n"
                    + "Generated: " + generatedAt + "\n"
                    + "========================================\n\n"
                    + String.format("%-24s %d%n", "Registered patients", lastReportCounts.getOrDefault("Patients", 0))
                    + String.format("%-24s %d%n", "Appointments", lastReportCounts.getOrDefault("Appointments", 0))
                    + String.format("%-24s %d%n", "Paid bills", lastReportCounts.getOrDefault("Bills", 0))
                    + "----------------------------------------\n"
                    + String.format("%-24s %s%n", "Total revenue", money(lastReportRevenue))
                    + "========================================\n\n"
                    + "Counts reflect current clinic database records.\n"
                    + "Revenue is calculated from all saved bills."
            );

        } catch (Exception e) {

            error(e);
        }
    }

    @FXML
    private void downloadReport() {
        try {
            requireAdmin();
            lastReportCounts = dao.reportCounts();
            lastReportRevenue = dao.totalRevenue();

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Download system report");
            chooser.setInitialFileName("sunrise-system-report-" + LocalDate.now() + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document", "*.pdf"));
            java.io.File selected = chooser.showSaveDialog(reportArea.getScene().getWindow());
            if (selected == null) {
                return;
            }
            Path output = selected.toPath();
            if (!output.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                output = output.resolveSibling(output.getFileName() + ".pdf");
            }
            createSystemReportPdf(output, lastReportCounts, lastReportRevenue);
            information("Report downloaded successfully to " + output.toAbsolutePath());
            openPdf(output);
        } catch (Exception e) {
            error(e);
        }
    }

    private void createSystemReportPdf(Path output, Map<String, Integer> counts,
            BigDecimal revenue) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        String generatedAt = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        StringBuilder content = new StringBuilder();
        content.append("0.059 0.09 0.165 rg 0 722 595 120 re f\n");
        content.append(pdfText(42, 787, 22, true, "SUNRISE DENTAL CLINIC", true));
        content.append(pdfText(42, 764, 10, false, "SYSTEM SUMMARY REPORT", true));
        content.append(pdfText(455, 764, 9, true, "ADMIN REPORT", true));
        content.append("0.059 0.09 0.165 rg\n");
        content.append(pdfText(42, 680, 15, true, "Clinic overview", false));
        content.append("0.392 0.455 0.545 rg\n");
        content.append(pdfText(42, 661, 9, false,
                "Operational totals at the time this report was generated.", false));

        String[] labels = {"PATIENTS", "APPOINTMENTS", "PAID BILLS"};
        String[] keys = {"Patients", "Appointments", "Bills"};
        int[] xs = {42, 237, 432};
        for (int i = 0; i < labels.length; i++) {
            content.append("0.973 0.98 0.988 rg ").append(xs[i]).append(" 557 121 76 re f\n");
            content.append("0.392 0.455 0.545 rg\n");
            content.append(pdfText(xs[i] + 12, 608, 8, true, labels[i], false));
            content.append("0.059 0.09 0.165 rg\n");
            content.append(pdfText(xs[i] + 12, 576, 22, true,
                    String.valueOf(counts.getOrDefault(keys[i], 0)), false));
        }

        content.append("0.945 0.961 0.976 rg 42 462 511 62 re f\n");
        content.append("0.059 0.09 0.165 rg\n");
        content.append(pdfText(58, 498, 10, true, "TOTAL REVENUE", false));
        content.append(pdfText(400, 480, 20, true, money(revenue), false));
        content.append("0.059 0.09 0.165 rg\n");
        content.append(pdfText(42, 417, 12, true, "Report notes", false));
        content.append("0.886 0.91 0.941 RG 42 405 m 553 405 l S\n");
        content.append("0.392 0.455 0.545 rg\n");
        content.append(pdfText(42, 382, 9, false,
                "Counts reflect current database records.", false));
        content.append(pdfText(42, 364, 9, false,
                "Revenue is calculated from all saved billing records.", false));
        content.append(pdfText(42, 338, 9, false, "Generated: " + generatedAt, false));
        content.append(pdfText(42, 42, 8, false,
                "Generated by Sunrise Dental Clinic Management System", false));
        content.append(pdfText(405, 42, 8, false,
                "Confidential - Admin only", false));
        writeSimplePdf(output, content.toString(), 595, 842);
    }

    // =========================================================
    // ROLE SECURITY
    // =========================================================
    private void requireAdmin() {

        if (currentUser == null
                || !"ADMIN".equals(
                        currentUser.role())) {

            throw new SecurityException(
                    "Administrator access required."
            );
        }
    }

    private void requireReceptionAccess() {

        if (currentUser == null) {

            throw new SecurityException(
                    "Please login first."
            );
        }

        boolean allowed
                = "ADMIN".equals(
                        currentUser.role())
                || "RECEPTIONIST".equals(
                        currentUser.role());

        if (!allowed) {

            throw new SecurityException(
                    "You do not have permission to perform this operation."
            );
        }
    }

    // =========================================================
    // LOGOUT
    // =========================================================
    @FXML
    private void logout() {

        try {

            Stage stage
                    = (Stage) lblUser
                            .getScene()
                            .getWindow();

            new SunriseDentalClinic()
                    .start(stage);

        } catch (Exception e) {

            error(e);
        }
    }

    // =========================================================
    // EXIT
    // =========================================================
    @FXML
    private void exit() {

        Stage stage
                = (Stage) lblUser
                        .getScene()
                        .getWindow();

        stage.close();
    }

    // =========================================================
    // VALIDATION
    // =========================================================
    private void required(
            TextField... fields
    ) {

        for (TextField field : fields) {

            if (field.getText() == null
                    || field
                            .getText()
                            .isBlank()) {

                throw new IllegalArgumentException(
                        "Please complete all required fields."
                );
            }
        }
    }

    private void clear(
            TextField... fields
    ) {

        for (TextField field : fields) {

            field.clear();
        }
    }

    // =========================================================
    // ALERTS
    // =========================================================
    private void error(
            Exception exception
    ) {

        String message
                = exception.getMessage();

        if (message == null
                || message.isBlank()) {

            message
                    = "An unexpected error occurred.";
        }

        Alert alert
                = new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                "Sunrise Dental Clinic"
        );

        alert.setHeaderText(
                "Error"
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    private void information(
            String message
    ) {

        Alert alert
                = new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(
                "Sunrise Dental Clinic"
        );

        alert.setHeaderText(
                "Success"
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }
}
