/**
 * ClientHandler.java
 *
 * This class is used to link the Server and Clients so that they can interact with each other.
 *
 */

// Import appropriate libraries
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable{
    // Initialized variables needed for player attributes to run the blackjack game or interact with others/server,
    // such as their hand total
    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    public Socket socket;
    public BufferedReader bufferedReader;
    public BufferedWriter bufferedWriter;
    public String clientUsername;

    public int handTotal;
    public String messageFromClient;
    public int win;

    public int wonGames;

    // ClientHandler constructor
    public ClientHandler(Socket socket){
        try{
            // Initialize all the attribute variables to the appropriate values.
            this.socket = socket;

            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            this.clientUsername = bufferedReader.readLine();

            this.handTotal = 0;
            this.win = -1;
            this.messageFromClient = " ";
            clientHandlers.add(this);
            broadcastMessage("Dealer: " + clientUsername + " has joined the table!");
        }catch (IOException e){
            closeEverything(socket, bufferedReader, bufferedWriter);
        }

    }

    // Function used that will broadcast the message this client sends to everyone
    @Override
    public void run() {
        while(socket.isConnected()){
            try{
                messageFromClient = bufferedReader.readLine();
                broadcastMessage(messageFromClient);
            }catch (IOException e){
                closeEverything(socket, bufferedReader, bufferedWriter);
                break;
            }
        }
    }

    // Function used to print out the passed in message for every client
    public void broadcastMessage(String messageToSend){
        for(ClientHandler clientHandler : clientHandlers){
            try{
                if(!clientHandler.clientUsername.equals(clientUsername)){
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            }catch (IOException e){
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }

    // Function used to remove client from clienthandlers arraylist if they disconnect
    public void removeClientHandler(){
        clientHandlers.remove(this);
        broadcastMessage("Dealer: " + clientUsername + " has left the table!");
    }

    // Function used to close every open attribute of the client when they disconnect
    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter){
        removeClientHandler();
        try{
            if(bufferedReader != null){
                bufferedReader.close();
            }
            if(bufferedWriter != null){
                bufferedWriter.close();
            }
            if(socket != null){
                socket.close();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

}