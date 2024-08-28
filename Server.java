/**
 * Server.java
 *
 * This class is used to run the Server, accept client connections, broadcast messages, connect/interact with
 * database and run blackjack games.
 *
 */

// Import necessary libraries
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;

public class Server {

    // Initialize needed variables for server to interact with players, interact with database and run blackjack games
    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private ServerSocket serverSocket;
    private int players = 0;
    private int dealerHandTotal = 0;
    public int min = 1;
    public int max = 11;
    public int random_int = 0;
    public int dealerLoses = 0;
    private int readies = 0;

    public int users_total_wins;


    public PreparedStatement preparedStatement;
    public Connection connection;

    // Server constructor
    // Create serversocket for clients to connect to
    public Server(ServerSocket serverSocket){
        this.serverSocket = serverSocket;
    }

    // Function to connect to database
    public void startDatabase(){
        try {
            Class.forName("com.sql.jdbc.Driver");
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println(e);
        }
        // Change url, username, and password to your database's information to allow proper connection
        String url = "jdbc:mysql://localhost:3306/blackjack?useSSL=false";
        String username = "root";
        String password = "root";

        System.out.println("Connecting database ...");

        try {
            connection = DriverManager.getConnection(url, username, password);
            System.out.println("Connection is successful !!!!!");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // Function that starts server, accepts connections, updates database, and runs blackjack games
    public void startServer()
    {
        try{
            while(!serverSocket.isClosed()){
                while (players != 2) {
                    // Accept any connections to socket to allow clients to connect
                    Socket socket = serverSocket.accept();
                    System.out.println("A new player has joined!");
                    ClientHandler clientHandler = new ClientHandler(socket);
                    String username = clientHandler.clientUsername;

                    // variable used to add ip of client/player to database
                    String user_ip = String.valueOf((socket.getLocalAddress()));

                    // sql queries to run
                    String insert_username = "INSERT INTO players (username, wins, IP) VALUES (?, ?, ?)";

                    //check if in first
                    String find_user = "SELECT * FROM players WHERE username = ?";
                    preparedStatement = connection.prepareStatement(find_user);
                    preparedStatement.setString(1, username);

                    //gets the username and if one isnt in the database, put it in
                    ResultSet resultSet = preparedStatement.executeQuery();
                    if (resultSet.next()) {
                        System.out.println("User is returner");
                    } else {
                        resultSet.close();
                        preparedStatement = connection.prepareStatement(insert_username);
                        preparedStatement.setString(1, clientHandler.clientUsername);
                        preparedStatement.setInt(2,0);
                        preparedStatement.setString(3, user_ip);
                        preparedStatement.execute();
                        System.out.println("Username does not exist. Creating profile");
                    }

                    // Run each client/connection on its own thread
                    Thread thread = new Thread(clientHandler);
                    thread.start();
                    players++;
                }

                // Wait for players to 'Ready' to start game
                broadcastMessage("Please enter 'Ready' when ready to play.");

                for(ClientHandler player : clientHandlers){
                    while (!player.messageFromClient.contains("Ready")) {
                        java.util.concurrent.TimeUnit.SECONDS.sleep(1);
                    }
                    readies += 1;
                }

                // If all players ready, start game

                while(readies == 2) {
                    blackjack();

                    // When game ends, reset everything
                    for(ClientHandler player : clientHandlers){
                        player.handTotal = 0;
                        player.messageFromClient = " ";
                        player.win = -1;
                    }
                    dealerLoses = 0;
                    dealerHandTotal = 0;
                    readies = 0;

                    //update wins in the database
                    for(ClientHandler player : clientHandlers) {
                        //gets players total wins from the database
                        String user_wins = "SELECT wins FROM players WHERE username = ?";
                        PreparedStatement preparedStatement1 = connection.prepareStatement(user_wins);
                        preparedStatement1.setString(1, player.clientUsername);
                        ResultSet resultSet1 = preparedStatement1.executeQuery();
                        if(resultSet1.next()){
                            users_total_wins = resultSet1.getInt("wins");
                        }

                        // Update wins
                        String update_wins = "UPDATE players SET wins = ? WHERE username = ?";
                        PreparedStatement preparedStatement2 = connection.prepareStatement(update_wins);
                        preparedStatement2.setInt(1,player.wonGames + users_total_wins);
                        preparedStatement2.setString(2,player.clientUsername);
                        int rows_updated = preparedStatement2.executeUpdate();
                        if(rows_updated > 0){
                            System.out.println("Wins updated successfully for user: " + player.clientUsername);
                        }
                    }



                }
            }
        }catch(IOException | InterruptedException | SQLException e){
        }
    }

    // Function that calls the necessary functions in appropriate order to correctly run blackjack game
    public void blackjack() throws InterruptedException {

        deal();

        hitOrStand();

        dealerDraws();

        winnings();

    }

    // Function for third part of a blackjack round where the dealer draws after all the players are done to get the best hand
    public void dealerDraws() throws InterruptedException {
        broadcastMessage("Dealer's Hand Total: " + dealerHandTotal);
        while(dealerHandTotal < 17){
            random_int = (int) Math.floor(Math.random() * (max - min + 1) + min);
            broadcastMessage("Dealer draws a " + random_int);
            dealerHandTotal += random_int;
            java.util.concurrent.TimeUnit.SECONDS.sleep(1);
        }
        broadcastMessage("Dealer's Hand Total: " + dealerHandTotal);
        if(dealerHandTotal > 21){
            broadcastMessage("Dealer Busts!");
            dealerLoses = 1;
        }
    }

    // Function for final part of a blackjack round where winners are decided and announced
    public void winnings(){
        for(ClientHandler player : clientHandlers){
            if(dealerLoses == 1 && player.handTotal <= 21){
                broadcastMessage(player.clientUsername + " wins!");
                player.wonGames=1;
            }else if(player.win == 1 && dealerHandTotal != 21){
                broadcastMessage(player.clientUsername + " wins!");
                player.wonGames=1;
            }else if(player.win == 1 && dealerHandTotal == 21){
                broadcastMessage(player.clientUsername + " draws with dealer.");
                player.wonGames = 0;
            }else if(player.win == 0){
                broadcastMessage(player.clientUsername + " loses.");
                player.wonGames = 0;
            }else if(player.handTotal == dealerHandTotal) {
                broadcastMessage(player.clientUsername + " draws with dealer!");
                player.wonGames = 0;
            }else if(player.handTotal > dealerHandTotal && player.handTotal <= 21){
                broadcastMessage(player.clientUsername + " wins!");
                player.wonGames=1;
            }else if(dealerHandTotal > player.handTotal && dealerHandTotal <= 21){
                broadcastMessage(player.clientUsername + " loses.");
                player.wonGames = 0;
            }
        }
    }

    // Function for first part of a blackjack round where the dealer deals the first pair of cards to players and themself
    public void deal() throws InterruptedException {
        broadcastMessage(" ");
        broadcastMessage("Dealer: Dealing first cards...");
        java.util.concurrent.TimeUnit.SECONDS.sleep(1);
        int random_int;
        for (ClientHandler player : clientHandlers) {
            random_int = (int) Math.floor(Math.random() * (max - min + 1) + min);
            player.handTotal += random_int;
            broadcastMessage(player.clientUsername + " draws a " + random_int);
            java.util.concurrent.TimeUnit.SECONDS.sleep(1);
        }

        random_int = (int) Math.floor(Math.random() * (max - min + 1) + min);
        broadcastMessage("Dealer draws a " + random_int);
        dealerHandTotal += random_int;
        java.util.concurrent.TimeUnit.SECONDS.sleep(3);

        broadcastMessage(" ");

        broadcastMessage("Dealer: Dealing Second cards...");
        java.util.concurrent.TimeUnit.SECONDS.sleep(1);
        for (ClientHandler player : clientHandlers) {
            random_int = (int) Math.floor(Math.random() * (max - min + 1) + min);
            broadcastMessage(player.clientUsername + " draws a " + random_int);
            player.handTotal += random_int;
            java.util.concurrent.TimeUnit.SECONDS.sleep(1);
        }

        random_int = (int) Math.floor(Math.random() * (max - min + 1) + min);
        broadcastMessage("Dealer draws an Unknown");
        dealerHandTotal += random_int;
        broadcastMessage(" ");
    }

    // Function for second part of a blackjack round where players can choose to hit or stand to get the best hand they possibly can.
    // If they get blackjack, they immediately win.
    // If they get over 21, they immediately lose.
    public void hitOrStand() throws InterruptedException {
        for (ClientHandler player : clientHandlers) {
            int temp = 0;
            broadcastMessage(player.clientUsername + "'s Hand Total: " + player.handTotal);
            broadcastMessage("Please enter Hit Me or I Stand " + player.clientUsername + ": ");
            while(temp == 0){
                if(player.handTotal < 21) {
                    while (!player.messageFromClient.contains("Hit Me") && !player.messageFromClient.contains("I Stand")) {
                        java.util.concurrent.TimeUnit.SECONDS.sleep(1);
                    }
                    if (player.messageFromClient.contains("Hit Me")) {
                        random_int = (int) Math.floor(Math.random() * (max - min + 1) + min);
                        broadcastMessage(player.clientUsername + " draws a " + random_int);
                        player.handTotal += random_int;
                        broadcastMessage(player.clientUsername + "'s Hand Total: " + player.handTotal);
                        if(player.handTotal < 21) {
                            broadcastMessage("Please enter Hit Me or I Stand " + player.clientUsername + ": ");
                        }
                        player.messageFromClient = " ";
                        java.util.concurrent.TimeUnit.SECONDS.sleep(1);
                    } else if (player.messageFromClient.contains("I Stand")) {
                        broadcastMessage(player.clientUsername + " stands with " + player.handTotal);
                        temp = 1;
                    }
                }else if(player.handTotal == 21){
                    broadcastMessage(player.clientUsername + " got a Blackjack!");
                    player.win = 1;
                    temp = 1;
                }else{
                    broadcastMessage(player.clientUsername + " busted!");
                    player.win = 0;
                    temp = 1;
                }
            }

            java.util.concurrent.TimeUnit.SECONDS.sleep(1);
            broadcastMessage(" ");
        }
    }

    // Function used to allow server to send messages to all clients
    public void broadcastMessage(String messageToSend){
        for(ClientHandler clientHandler : clientHandlers){
            try{
                clientHandler.bufferedWriter.write(messageToSend);
                clientHandler.bufferedWriter.newLine();
                clientHandler.bufferedWriter.flush();
            }catch (IOException e){
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1234);
        Server server = new Server(serverSocket);
        clientHandlers = ClientHandler.clientHandlers;
        server.startDatabase();
        server.startServer();

    }

}