package it.fi.itismeucci.chat;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client handler del server
 * 
 * gestisce il client dal punto di vista del server
 * 
 */
public class ClientHandler extends Thread {

    private Socket socket;
    private String nomeUtente;
    private BufferedReader input;
    private DataOutputStream output;
    private ObjectMapper objectMapper;
    private Messaggio mexInviato;
    private Messaggio mexRicevuto;
    private Messaggio utente;
    private static String listaClientConnessi = "";

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.output = new DataOutputStream(socket.getOutputStream());
        this.objectMapper = new ObjectMapper();
        this.mexInviato = new Messaggio();
        this.utente = new Messaggio();
    }

    public String getNomeUtente() {
        return nomeUtente;
    }

    @Override
    public void run() {
        // si effettua la registrazione
        try {
            registrazione(socket);
        } catch (IOException e) {
            System.out.println(e);
        }
        // si notifica del nuovo client in chat
        try {
            notificaClients(socket, Colori.ANSI_YELLOW + "Notifica --> " + Colori.ANSI_RESET + Colori.ANSI_CYAN + nomeUtente + Colori.ANSI_RESET + " si e' unito alla chat!");
        } catch (Exception e) {
            System.out.println(e);
        }
        // rivevi messaggi
        try {
            for (;;)
                riceviMessaggio(input.readLine());
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void registrazione(Socket socket) throws IOException {
        boolean exists;
        do {
            exists = false;
            // ricevo il messaggio e lo deserializzo
            utente = deserializzaMessaggio(input.readLine());
            System.out.println("Utente connesso come: " + utente.getMittente());
            for (ClientHandler c : ServerStr.listaClient) {
                // controllo che il nome utente non sia esistente
                if (utente.getMittente().equals(c.getNomeUtente())) {
                    // var impostata su true per ripetere il ciclo
                    exists = true;
                    // invio messaggio di errore
                    invioMessaggioServer("Connessione rifiutata, client gia' esistente");
                    break;
                }
            }
        } while (exists);
        // il client non è un doppioneallora lo aggiungo il client alla lista
        ServerStr.listaClient.add(this);
        // imposto il nome del client
        nomeUtente = utente.getMittente();
        // aggiungo il nome all'arraylist
        ServerStr.allClientsName.add(nomeUtente);
        // conferma da parte del server che il client si è connesso alla chat
        invioMessaggioServer("entrato");
        
    }

    public void invioMessaggioServer(String mex) throws IOException {
        mexInviato.setMittente("Server");
        mexInviato.setComando("0");
        mexInviato.setDestinatario(null);
        mexInviato.setCorpo(mex);
        inviaMessaggio(mexInviato);
    }

    public void messaggioErrore(String err) throws IOException {
        mexInviato.setMittente("Server");
        mexInviato.setComando("0");
        mexInviato.setDestinatario(null);
        mexInviato.setCorpo("Errore nel server. \n" + err);
        inviaMessaggio(mexInviato);
        System.out.println(err);
    }

    public void inviaMessaggio(Messaggio mexInviato) throws IOException {
        // serializzo
        String stringaSerializzata = objectMapper.writeValueAsString(mexInviato);
        // scrivo il messaggio
        output.writeBytes(stringaSerializzata + '\n');
    }

    public Messaggio deserializzaMessaggio(String messaggioRicevuto)
            throws JsonMappingException, JsonProcessingException {
        // deserializzo
        Messaggio stringaDeserializzata = objectMapper.readValue(messaggioRicevuto, Messaggio.class);
        // ritorno il l'istanza
        return stringaDeserializzata;
    }

    public void notificaClients(Socket socket, String messaggio) throws IOException {
        // il client è entrato a far parte della chat e lo notifica
        if(ServerStr.allClientsName.size() == 1){
            invioMessaggioServer(Colori.ANSI_YELLOW + "Notifica --> " + Colori.ANSI_RESET + "Sei il solo connesso alla chat");
        }
        for (ClientHandler c : ServerStr.listaClient) {
            try {
                if (!c.nomeUtente.equals(this.nomeUtente)) {
                    c.invioMessaggioServer(messaggio);
                }
            } catch (IOException e) {
                messaggioErrore("Errore nell'invio del messaggio broadcast dal server");
            }
        }
    }

    public void riceviMessaggio(String messaggioRicevuto) throws IOException {
        mexRicevuto = deserializzaMessaggio(messaggioRicevuto);
        // messaggio broadcast
        if (mexRicevuto.getComando().equals("1")) {
            // scrivo sul server la situazione
            System.out.println(this.nomeUtente + " ha inviato a tutti: " + mexRicevuto.getCorpo());
            for (ClientHandler c : ServerStr.listaClient) {
                try {
                    if (!c.nomeUtente.equals(this.nomeUtente)) {
                        c.inviaMessaggio(mexRicevuto);
                    }
                } catch (IOException e) {
                    System.out.println(e);
                    messaggioErrore("Errore nell'invio del messaggio broadcast dal server");
                }
            }
        }
        // messaggio diretto ad una sola persona
        else if (mexRicevuto.getComando().equals("2") || mexRicevuto.getComando().equals("risposta")) {
            boolean exists = false;
            for (ClientHandler c : ServerStr.listaClient) {
                if (c.nomeUtente.equals(mexRicevuto.getDestinatario().get(0))) {
                    c.inviaMessaggio(mexRicevuto);
                    System.out.println(this.nomeUtente + " ha inviato a " + mexRicevuto.getDestinatario() + ": "
                            + mexRicevuto.getCorpo());
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                System.out.println(this.nomeUtente + " ha inviato a " + mexRicevuto.getDestinatario() + ": "
                        + mexRicevuto.getCorpo() + ". Ma l'utente non esiste.");
                invioMessaggioServer("Utente non esistente");
            }
        } else if (mexRicevuto.getComando().equals("-1")) {
            mexRicevuto.setDestinatario(mexRicevuto.getDestinatario());
            mexRicevuto.setMittente("Server");
            mexRicevuto.setCorpo(listaClientConnessi());
            // invio la lista degli utenti connessi
            inviaMessaggio(mexRicevuto);
        }
        // rimozione del client
        else if (mexRicevuto.getComando().equals("4")) {
            for (ClientHandler c : ServerStr.listaClient) {
                try {
                    if (!c.nomeUtente.equals(this.nomeUtente)) {
                        mexRicevuto.setCorpo(this.nomeUtente + " e' uscito dalla chat!");
                        mexRicevuto.setComando("chiusura");
                        c.inviaMessaggio(mexRicevuto);
                    }
                } catch (IOException e) {
                    System.out.println(e);
                    messaggioErrore("Errore nell'invio del messaggio broadcast dal server");
                }
            }
            ServerStr.listaClient.remove(this);
            ServerStr.allClientsName.remove(nomeUtente);
            mexRicevuto.setDestinatario(mexRicevuto.getDestinatario());
            mexRicevuto.setComando("4");
            mexRicevuto.setMittente("Server");
            System.out.println(nomeUtente + " si è disconnesso");
            inviaMessaggio(mexRicevuto);
        }
        mexRicevuto.setComando(null);
        mexRicevuto.setCorpo(null);
        mexRicevuto.setDestinatario(null);
        mexRicevuto.setMittente(null);
    }

    public String listaClientConnessi() throws IOException {
        listaClientConnessi = "";
        for (ClientHandler c : ServerStr.listaClient) {
            listaClientConnessi = listaClientConnessi + "-> " + c.nomeUtente + "\n";
        }
        return listaClientConnessi;
    }
}
