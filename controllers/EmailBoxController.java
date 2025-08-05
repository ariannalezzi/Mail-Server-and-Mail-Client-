package com.example.mieproveprogetto.controllers;

import com.example.mieproveprogetto.ClientMain;
import com.example.mieproveprogetto.model.Email;
import com.example.mieproveprogetto.model.User;
import com.example.mieproveprogetto.utils.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmailBoxController {
    @FXML
    private ListView<Email> sideMailList;
    @FXML
    private WebView textArea;
    @FXML
    private Label lblUser;
    @FXML
    private Label lblSubject;
    @FXML
    private Label lblFrom;
    @FXML
    private Label lblDate;
    private static Email selectedEmail;
    private final Email emptyEmail = new Email(-1, "", new ArrayList<>(), "", "", "");
    private static User usr;
    private static String my_address;
    private static int action;
    private static int lastIdReceived;

    public EmailBoxController() {
        selectedEmail = null;
        sideMailList = new ListView<>();
        lblUser = new Label();
        lblFrom = new Label();
        lblSubject = new Label();
        lblDate = new Label();
        textArea = new WebView();
        lastIdReceived = -1;
    }

    public void initialize() {
        usr = new User(LoginController.addressProperty().get());
        my_address = usr.getAddress();

        requestLoadInbox();

        sideMailList.itemsProperty().bind(usr.inboxProperty());
        sideMailList.setOnMouseClicked(this::showSelectedEmail);
        lblUser.textProperty().bind(usr.addressProperty());
        updateDetailView(emptyEmail);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Socket mySocket = new Socket(InetAddress.getLocalHost().getHostName(), 9090);
                ObjectOutputStream writer = new ObjectOutputStream(mySocket.getOutputStream());
                Message message = new Message(my_address,"Ping", "User " + my_address + " trying to ping");
                writer.writeObject(message);
                writer.flush();

                ObjectInputStream reader = new ObjectInputStream(mySocket.getInputStream());
                Message response = (Message) reader.readObject();
                reader.close();
                writer.close();
                mySocket.close();

                if((Boolean) response.getContent()) {
                    Platform.runLater(
                      () -> requestFillInbox()
                    );
                } else {
                    throw new RuntimeException();
                }
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                System.out.println("Server down");
            }
        }, 5, 5, TimeUnit.SECONDS);

    }
    private static void requestLoadInbox() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            Socket mySocket = new Socket(host, 9090);

            ObjectOutputStream writer = new ObjectOutputStream(mySocket.getOutputStream());
            Message message = new Message(my_address,"LoadInbox", my_address);
            writer.writeObject(message);
            writer.flush();

            ObjectInputStream reader = new ObjectInputStream(mySocket.getInputStream());
            Message response = (Message) reader.readObject();
            if(response.getAction().equals("LoadInbox start")) {
                int size = (Integer) response.getContent();
                for(int i=0; i<size; i++) {
                    Email newMail = (Email) ((Message) reader.readObject()).getContent();
                    lastIdReceived = newMail.getId();
                    usr.add(newMail);
                }
            }
            Message end = (Message) reader.readObject();
            reader.close();
            writer.close();
            mySocket.close();

            if(!(end.getAction().equals("End"))) {
                Platform.runLater(
                    () -> {
                        Alert newAlert = new Alert(Alert.AlertType.ERROR);
                        newAlert.setTitle("ERROR");
                        newAlert.setHeaderText("Load inbox");
                        newAlert.setContentText("Something went wrong while loading " + my_address + " 's inbox");
                        newAlert.show();
                    }
                );
            }
        } catch (Exception e) {
            Platform.runLater(
                () -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("ERROR");
                    alert.setHeaderText("Server down");
                    alert.setContentText("Server down, can't load user inbox");
                    alert.show();
                }
            );
        }
    }
    private static void requestFillInbox() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            Socket mySocket = new Socket(host, 9090);

            ObjectOutputStream writer = new ObjectOutputStream(mySocket.getOutputStream());

            Message message = new Message(my_address,"FillInbox", lastIdReceived);
            writer.writeObject(message);
            writer.flush();

            ObjectInputStream reader = new ObjectInputStream(mySocket.getInputStream());
            Message response = (Message) reader.readObject();
            int length = (Integer) response.getContent();
            if(length == 0) {
                writer.close();
                reader.close();
                mySocket.close();
            } else {
                ArrayList<Email> mailsToAdd = new ArrayList<>();
                for(int i=0; i<length; i++) {
                    Email newMail = (Email) ((Message) reader.readObject()).getContent();
                    mailsToAdd.add(newMail);
                    lastIdReceived = newMail.getId();
                }
                writer.close();
                reader.close();
                mySocket.close();
                Platform.runLater(
                    () -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("NEW MAIL");
                        alert.setHeaderText("New Mail for you!");
                        String greetings = my_address.substring(0, my_address.indexOf("."));
                        if(length == 1)
                            alert.setContentText("Hi " + greetings + "!" +  "\nYou have a new mail, check your inbox!");
                        else
                            alert.setContentText("Hi " + greetings + "!" +  "\nYou have " + length + " new mails, check your inbox!");
                        alert.show();
                    }
                );
                for (Email e : mailsToAdd)
                    usr.add(e);
            }

        } catch (Exception e) {
            System.out.println("Error client side while trying to fill the inbox: " + e.getMessage());
            Platform.runLater(
                () -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("WARNING");
                    alert.setHeaderText("Server down");
                    alert.setContentText("Server down, can't update the inbox");
                    alert.show();
                }
            );
        }
    }
    private boolean requestDeleteMail(Email target, String address) {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            Socket mySocket = new Socket(host, 9090);

            ObjectOutputStream writer = new ObjectOutputStream(mySocket.getOutputStream());
            Message message = new Message(my_address, "DeleteMail", target);
            writer.writeObject(message);
            writer.flush();

            ObjectInputStream reader = new ObjectInputStream(mySocket.getInputStream());
            Message response = (Message) reader.readObject();
            reader.close();
            writer.close();
            mySocket.close();
            if((Boolean) response.getContent()) {
                return true;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            System.out.println("Error while trying to delete an email from " + address + " 's inbox");
        }
        return false;
    }
    @FXML
    private void delete() {
        if(requestDeleteMail(selectedEmail, my_address)) {
            usr.inboxProperty().remove(selectedEmail);
            usr.decCounterMail();
            updateDetailView(emptyEmail);
        }
    }
    @FXML
    private void newMail() {
        try {
            action = 0;
            NewEmailController newEmailController = new NewEmailController();
            Parent newEmailParent = FXMLLoader.load(Objects.requireNonNull(ClientMain.class.getResource("NewEmail.fxml")));
            Stage newEmailStage = new Stage();
            newEmailStage.setScene(new Scene(newEmailParent));
            newEmailStage.show();
        } catch (IOException e) {
            System.out.println("An error occurred while creating a new mail: \n" + e.getMessage());
            e.printStackTrace();
        }
    }
    @FXML
    private void reply() {
        try {
            action = 1;
            NewEmailController newEmailController = new NewEmailController();
            Parent replyParent = FXMLLoader.load(Objects.requireNonNull(ClientMain.class.getResource("NewEmail.fxml")));

            selectedEmail = sideMailList.getSelectionModel().getSelectedItem();

            Stage replyStage = new Stage();
            replyStage.setScene(new Scene(replyParent));
            replyStage.show();
        } catch (IOException e) {
            System.out.println("An error occurred while replying to a mail: \n" + e.getMessage());
        }
    }
    @FXML
    private void replyAll() {
        try {
            action = 2;
            NewEmailController newEmailController = new NewEmailController();
            Parent replyAllParent = FXMLLoader.load(Objects.requireNonNull(ClientMain.class.getResource("NewEmail.fxml")));

            selectedEmail = sideMailList.getSelectionModel().getSelectedItem();

            Stage replyAllStage = new Stage();
            replyAllStage.setScene(new Scene(replyAllParent));
            replyAllStage.show();
        } catch (IOException e) {
            System.out.println("An error occurred while replying to everyone: \n" + e.getMessage());
        }
    }
    @FXML
    private void forward() {
        try {
            action = 3;
            NewEmailController newEmailController = new NewEmailController();
            Parent forwardParent = FXMLLoader.load(Objects.requireNonNull(ClientMain.class.getResource("NewEmail.fxml")));

            selectedEmail = sideMailList.getSelectionModel().getSelectedItem();

            Stage forwardStage = new Stage();
            forwardStage.setScene(new Scene(forwardParent));
            forwardStage.show();
        } catch (IOException e) {
            System.out.println("An error occurred while forwarding an email: \n" + e.getMessage());
        }
    }
    protected void showSelectedEmail(MouseEvent mouseEvent) {
        Email email = sideMailList.getSelectionModel().getSelectedItem();
        selectedEmail = email;
        updateDetailView(email);
    }
    private void updateDetailView(Email mail) {
        if(mail != null) {
            lblFrom.setText(mail.getSender());
            lblSubject.setText(mail.getSubject());
            lblDate.setText(mail.getDate());
            textArea.getEngine().loadContent(mail.getText());
        }
    }
    public static User getCurrentUser() { return usr; }
    public static int getAction() { return action; }
    public static String getSender() {
        return selectedEmail.getSender();
    }
    public static String getSubject() {
        return "RE: " + selectedEmail.getSubject();
    }
    public static String getReceiver() {
        String ris = "(";
        for(String rec : selectedEmail.getReceivers())
            ris += rec + ",";
        ris += ")";
        ris = ris.replace(",)", ")").replace("(", "").replace(")", "");
        ris = ris.replace(my_address+",", "");
        return ris;
    }

    public static String getText() {
        return "Sender: " + selectedEmail.getSender() + "\n Text: " + selectedEmail.getText();
    }
}
