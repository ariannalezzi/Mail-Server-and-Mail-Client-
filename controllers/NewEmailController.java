package com.example.mieproveprogetto.controllers;

import com.example.mieproveprogetto.model.Email;
import com.example.mieproveprogetto.model.User;
import com.example.mieproveprogetto.utils.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.HTMLEditor;
import javafx.stage.Stage;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewEmailController {
    @FXML
    public Label lblFrom;
    @FXML
    public TextField toField;
    @FXML
    public TextField subjectField;
    @FXML
    public HTMLEditor txtEmail;

    private User usr;

    public NewEmailController() {
        lblFrom = new Label();
        toField = new TextField();
        subjectField = new TextField();
        txtEmail = new HTMLEditor();
    }

    @FXML
    public void initialize(){
        usr = EmailBoxController.getCurrentUser();
        lblFrom.textProperty().bind(usr.addressProperty());
        if(EmailBoxController.getAction() == 1) {
            toField.setText(EmailBoxController.getSender());
            subjectField.setText(EmailBoxController.getSubject());
        }
        if(EmailBoxController.getAction() == 2) {
            toField.setText(EmailBoxController.getSender() + "," + EmailBoxController.getReceiver());
            subjectField.setText(EmailBoxController.getSubject());
        }
        if(EmailBoxController.getAction() == 3) {
            subjectField.setText(EmailBoxController.getSubject());
            txtEmail.setHtmlText(EmailBoxController.getText());
        }
    }

    @FXML
    public void sendEmail(MouseEvent event) {
        String sender = lblFrom.getText();
        String rec = toField.getText();
        ArrayList<String> receivers = checkSyntax(rec);

        if(receivers != null) {
            String subject = subjectField.getText();
            String text = txtEmail.getHtmlText().replace("contenteditable=\"true\"", "contenteditable=\"false\"");
            Email mailToSend = new Email(sender, receivers, subject, text);

            ObjectOutputStream writer = null;
            ObjectInputStream reader = null;
            Socket mySocket = null;

            Message message = new Message(mailToSend.getSender(),"Mail", mailToSend);
            try {
                String host = InetAddress.getLocalHost().getHostName();
                mySocket = new Socket(host, 9090);

                writer = new ObjectOutputStream(mySocket.getOutputStream());
                writer.writeObject(message);
                writer.flush();

                reader = new ObjectInputStream(mySocket.getInputStream());
                Object response = null;

                do {
                    response = reader.readObject();
                } while(!(response instanceof Message));

                Message m = (Message) response;
                boolean content = (Boolean) m.getContent();
                if(content) {
                    writer.close();
                    reader.close();
                    mySocket.close();
                    cancel(event);
                } else {
                    System.out.println(m.getAction());
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    writer.close();
                    reader.close();
                    mySocket.close();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        } else {
            Platform.runLater(
                () -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("WARNING");
                    alert.setHeaderText("Syntax error");
                    alert.setContentText(
                        "At least one of the addresses you are trying to send an email to " +
                        "doesn't have the syntax of an email"
                    );
                    alert.show();
                }
            );
        }
    }

    private ArrayList<String> checkSyntax(String rec) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\."+
          "[a-zA-Z0-9_+&*-]+)*@" +
          "(?:[a-zA-Z0-9-]+\\.)+[a-z" +
          "A-Z]{2,7}$";
        Pattern pat = Pattern.compile(emailRegex);
        ArrayList<String> receivers = new ArrayList<>();

        if(rec.contains(",")) {
            String[] temp = rec.split(",");
            for(String s : temp) {
                if(pat.matcher(s).matches())
                    receivers.add(s);
                else
                    return null;
            }
            return receivers;
        } else {
            if(pat.matcher(rec).matches()) {
                receivers.add(rec);
                return receivers;
            }
            else
                return null;
        }
    }
    @FXML
    public void cancel(MouseEvent event) {
        Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
