package com.example.mieproveprogetto.model;

import com.example.mieproveprogetto.controllers.ServerController;
import com.example.mieproveprogetto.utils.Message;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ServerRunnable implements Runnable {
    private final ServerModel model;
    private Socket socket;
    private final ObjectInputStream objectInputStream;
    private final ObjectOutputStream objectOutputStream;

    public ServerRunnable(Socket socket) throws IOException {
        this.socket = socket;
        this.objectInputStream = new ObjectInputStream(this.socket.getInputStream());
        this.objectOutputStream = new ObjectOutputStream(this.socket.getOutputStream());
        this.model = ServerController.getCurrentModel();
    }

    @Override
    public void run() {
        try {
            Object obj = objectInputStream.readObject();
            if(obj instanceof Message) {
                Message message = (Message) obj;
                String action = message.getAction();
                if(action.equals("Ping")) {
                    Message response = null;
                    if(ServerController.isRunning())
                        response = new Message(message.getAddress(), "I'm running", true);
                    else
                        response = new Message(message.getAddress(), "I'm not running", false);

                    objectOutputStream.writeObject(response);
                }
                if(action.equals("Login")) {
                    if(ServerController.isRunning()) {
                        String address = (String) message.getContent();
                        boolean result = authenticate(address);
                        if (result) {
                            objectOutputStream.writeObject(new Message(address, "Login successful", true));
                            Platform.runLater(
                                () -> model.addToListLog("User " + address + " just logged in")
                            );
                        } else {
                            objectOutputStream.writeObject(new Message(address,"Login unsuccessful - this mail doesn't exists", false));
                            Platform.runLater(
                                () -> {
                                    Alert alert = new Alert(Alert.AlertType.WARNING);
                                    alert.setTitle("WARNING");
                                    alert.setHeaderText("Login unsuccessful");
                                    alert.setContentText("This mail doesn't exist");
                                    alert.show();
                                }
                            );
                        }
                    } else {
                        objectOutputStream.writeObject(new Message("","Login unsuccessful - server down", false));
                        Platform.runLater(
                            () -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("ERROR");
                                alert.setHeaderText("Login unsuccessful");
                                alert.setContentText("Server is down right now, try later");
                                alert.show();
                            }
                        );
                    }
                }
                if(action.equals("LoadInbox")) {
                    if(ServerController.isRunning()) {
                        String address = (String) message.getContent();
                        Platform.runLater(
                            () -> model.addToListLog("Successfully loaded " + address + " 's inbox")
                        );
                        loadInbox(address);
                    } else {
                        objectOutputStream.writeObject(new Message("","LoadInbox unsuccessful - server down", false));
                    }
                }
                if(action.equals("FillInbox")) {
                    String address = message.getAddress();
                    if(ServerController.isRunning()) {
                        int lastIdSent = (Integer) message.getContent();
                        ArrayList<Email> mailsToSend = fillInbox(address, lastIdSent);
                        int length = mailsToSend.size();
                        if(length == 0)
                            objectOutputStream.writeObject(new Message(address, "No updates", length));
                        else {
                            objectOutputStream.writeObject(new Message(address, "Updates for you", length));
                            for(Email e : mailsToSend)
                                objectOutputStream.writeObject(new Message(address, "New Mail", e));
                            objectOutputStream.writeObject(new Message(address, "End of communications", null));
                        }
                    } else {
                        objectOutputStream.writeObject(new Message(address,"FillInbox unsuccessful - server down", false));
                    }
                }
                if(action.equals("Mail")) {
                    if(ServerController.isRunning()) {
                        Email emailToSend =  (Email) message.getContent();
                        for(String dest : emailToSend.getReceivers()) {
                            if(authenticate(dest)) {
                                updateUserFile(dest, emailToSend);
                                objectOutputStream.writeObject(new Message(dest, "Email sent without errors", true));
                                Platform.runLater(
                                    () -> {
                                        model.addToListLog(emailToSend.getSender() + " just sent an email to " + dest);
                                        model.addToListLog(dest + " just received an email from " + emailToSend.getSender());
                                    }
                                );
                            } else {
                                objectOutputStream.writeObject(new Message(dest, "Couldn't send this email to user " + dest + " because it doesn't exist", false));
                                Platform.runLater(
                                    () -> {
                                        model.addToListLog("Error while sending an email from " + emailToSend.getSender() + " to " + dest);
                                        Alert alert = new Alert(Alert.AlertType.WARNING);
                                        alert.setTitle("SEND GONE WRONG");
                                        alert.setHeaderText("Couldn't send the email to user " + dest);
                                        alert.setContentText("The user " + dest + " doesn't exist");
                                        alert.show();
                                    }
                                );
                            }
                        }
                    } else {
                        objectOutputStream.writeObject(new Message("", "Couldn't send the email - server down", false));
                        Platform.runLater(
                            () -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("ERROR");
                                alert.setHeaderText("Send email unsuccessful");
                                alert.setContentText("Server is down right now, try later");
                                alert.show();
                            }
                        );
                    }
                }
                if(action.equals("DeleteMail")) {
                    if(ServerController.isRunning()) {
                        Email emailToDelete = (Email) message.getContent();
                        boolean ris = deleteMailFromInbox(emailToDelete, message.getAddress());
                        if(ris) {
                            objectOutputStream.writeObject(new Message(message.getAddress(), "Delete successful", true));
                            Platform.runLater(
                                () -> model.addToListLog(message.getAddress() + " just deleted an email ")
                            );
                        } else
                            objectOutputStream.writeObject(new Message(message.getAddress(), "Delete unsuccessful", false));
                    } else {
                        objectOutputStream.writeObject(new Message(message.getAddress(), "Delete unsuccessful", false));
                        Platform.runLater(
                            () -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("ERROR");
                                alert.setHeaderText("Delete mail unsuccessful");
                                alert.setContentText("Server is down right now, try later");
                                alert.show();
                            }
                        );
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println();
        }
    }
    private synchronized void updateUserFile(String address, Email content) {
        File inputFile = new File("/Users/ari3/Desktop/MieProveProgetto/src/main/java/com/example/mieproveprogetto/utils/receipts/"+address+".txt");

        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new BufferedReader(new FileReader(inputFile)));
            BufferedWriter writer = new BufferedWriter(new FileWriter(inputFile));

            JSONObject obj = new JSONObject();
            obj.put("id", content.getId());
            obj.put("sender", content.getSender());
            obj.put("receiver", content.getReceivers());
            obj.put("subject", content.getSubject());
            obj.put("text", content.getText());
            obj.put("date", content.getDate());
            array.add(obj);

            writer.write("[\n");
            for(Object o : array) {
                JSONObject json_mail = (JSONObject) o;
                writer.write(json_mail.toString() + "\n,");
            }
            writer.write("]\n");
            writer.close();
        } catch (IOException | ParseException e) {
            System.out.println("Error while reading from " + address + ".txt in order to delete a mail. \n" + e.getMessage());
        }
    }
    private synchronized void loadInbox(String address) {
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new BufferedReader(new FileReader("/Users/ari3/Desktop/MieProveProgetto/src/main/java/com/example/mieproveprogetto/utils/receipts/"+address+".txt")));
            int size = array.size();

            this.objectOutputStream.writeObject(new Message(address, "LoadInbox start", size));
            for(Object obj : array) {
                JSONObject mail = (JSONObject) obj;

                int id = ( (Long) mail.get("id") ).intValue();
                String sender = (String) mail.get("sender");
                String subject = (String) mail.get("subject");
                String text = (String) mail.get("text");
                String date = (String) mail.get("date");

                ArrayList<String> receivers = new ArrayList<>();
                JSONArray json_receivers = (JSONArray) mail.get("receiver");
                for(Object o : json_receivers) {
                    String rec = (String) o;
                    receivers.add(rec);
                }

                Email new_email = new Email(id, sender, receivers, subject, text, date);
                this.objectOutputStream.writeObject(new Message(address, "This is an email", new_email));
                User.incCounterMail();
            }
            this.objectOutputStream.writeObject(new Message(address, "End", "End"));
        } catch (IOException | ParseException e) {
            System.out.println("Error in filling the inbox while reading " + address + " file: \n" + e.getMessage());
        }
    }
    private synchronized ArrayList<Email> fillInbox(String address, int lastId) {
        ArrayList<Email> mails = new ArrayList<>();
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new BufferedReader(new FileReader("/Users/ari3/Desktop/MieProveProgetto/src/main/java/com/example/mieproveprogetto/utils/receipts/"+address+".txt")));

            for(Object obj : array) {
                JSONObject mail = (JSONObject) obj;

                int id = ( (Long) mail.get("id") ).intValue();
                if(id > lastId) {
                    String sender = (String) mail.get("sender");
                    String subject = (String) mail.get("subject");
                    String text = (String) mail.get("text");
                    String date = (String) mail.get("date");

                    JSONArray json_receivers = (JSONArray) mail.get("receiver");
                    ArrayList<String> receivers = new ArrayList<>();
                    for(Object o : json_receivers) {
                        String rec = (String) o;
                        receivers.add(rec);
                    }

                    Email new_email = new Email(id, sender, receivers, subject, text, date);
                    mails.add(new_email);
                    User.incCounterMail();
                }
            }
            return mails;
        } catch (IOException | ParseException e) {
            System.out.println("Error in filling the inbox while reading " + address + " file: \n" + e.getMessage());
        }
        return mails;
    }
    public synchronized boolean deleteMailFromInbox(Email mail, String address) {
        File inputFile = new File("/Users/ari3/Desktop/Uni/MieProveProgetto/src/main/java/com/example/mieproveprogetto/utils/receipts"+ address +".txt");

        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new BufferedReader(new FileReader(inputFile)));
            BufferedWriter writer = new BufferedWriter(new FileWriter(inputFile));

            writer.write("[\n");
            for(Object o : array) {
                JSONObject json_mail = (JSONObject) o;
                int id = ( (Long) json_mail.get("id") ).intValue();
                if(id != mail.getId()) {
                  writer.write(json_mail.toJSONString() + "\n,");
                }
            }
            writer.write("]\n");
            writer.close();
            return true;
        } catch (IOException | ParseException e) {
            System.out.println("Error while reading from " + address + ".txt in order to delete a mail. \n" + e.getMessage());
        }
        return false;
    }
    private synchronized boolean authenticate(String mail) {
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new BufferedReader(new FileReader("/Users/ari3/Desktop/Uni/MieProveProgetto/src/main/java/com/example/mieproveprogetto/utils/accounts.txt")));

            for(Object o : array) {
                JSONObject obj = (JSONObject) o;
                String email = (String) obj.get("email");
                if(email.equals(mail)) {
                    return true;
                }
            }

          } catch (IOException | ParseException e) {
              System.out.println("Error while trying to authenticate the user: \n" + e.getMessage());
          }
        return false;
    }
}
