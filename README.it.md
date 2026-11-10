> [!NOTE]
> **L'intero progetto è stato realizzato in vibe coding. In questo repository non esiste codice scritto da un umano.**

[Read this in English](README.md)

# WoW

L’interfaccia parte in inglese. Il pulsante **Italiano / English** nella schermata principale cambia lingua subito e conserva la scelta per gli avvii successivi.

App Android `net.alessio.wow`, Android 8 o successivo, con interfaccia Material 3 Expressive e profili salvati sulla home.

I nuovi profili SSH richiedono l’inserimento esplicito della porta. Le porte dei profili già salvati vengono mantenute.

## Risveglio tramite router SSH

Il profilo SSH accede al router con una chiave del dispositivo oppure con utente/password. In **Comando Wake-on-LAN** puoi scegliere una delle sintassi preconfigurate:

| Preset | Comando |
| --- | --- |
| AsusWRT / Merlin | `ether-wake -i {interface} {broadcast} {mac}` |
| OpenWrt | `etherwake -i {interface} {broadcast} {mac}` |
| MikroTik RouterOS | `/tool wol mac={mac} interface={interface}` |
| Advantech CLI | `ether-wake -i {interface} {broadcast} {mac}` |

Sotto ogni nome trovi l’anteprima della sintassi. Il preset OpenWrt richiede che il pacchetto `etherwake` sia installato sul router.

**Nuovo preset** apre un modulo vuoto per nome e comando. Il comando ammette una riga con argomenti semplici. `{mac}` usa il MAC del PC, `{interface}` usa l’interfaccia LAN del profilo e `{broadcast}` diventa `-b` quando attivo. Puoi creare più preset e rimuoverli singolarmente. I profili conservano una copia del preset usato, quindi la rimozione dalla libreria lascia disponibili i comandi già configurati.

Ogni firmware deve consentire al proprio utente SSH di eseguire il comando scelto. Verifica il nome dell’interfaccia LAN sul tuo router. Un codice di uscita zero conferma che il comando è terminato con successo. Il risveglio effettivo del PC va verificato separatamente.

Al primo collegamento, WoW si ferma prima dell’autenticazione e mostra l’impronta SHA-256 della chiave del router. Confrontarla con una chiave conosciuta tramite un accesso fidato. Dopo l'associazione premere nuovamente Risveglia. Una chiave diversa da quella salvata blocca la connessione; per riassegnarla occorre Modifica > Dimentica chiave e una nuova verifica. La lettura della chiave dal modulo di modifica non invia il comando WoL né autentica l'utente.

L'interfaccia di rete del dispositivo da risvegliare deve supportare WOL ed essere configurata correttamente.
Se SSH sul router è impostato Solo LAN, da Internet occorre una VPN verso la rete di casa oppure un accesso SSH remoto configurato esplicitamente. Il DDNS non abilita l'accesso remoto da solo.

## Credenziali e profili

I profili vengono cifrati al salvataggio con AES-GCM e una chiave non esportabile in Android Keystore. La password non viene inserita nello stato salvato dell'Activity o nei log. La disinstallazione, cancellazione dati o perdita della chiave rendono irrecuperabili i profili cifrati. La modalità Chiave dispositivo usa una chiave ECDSA P-256 generata in Android Keystore e un adattatore JSch che firma tramite il provider senza esportare la privata. La home permette di generare la chiave una sola volta e copiare esclusivamente la riga pubblica OpenSSH. La modalità con chiave non salva password né tenta autenticazione con password. La protezione hardware dipende dal dispositivo: StrongBox non è forzato né attestato. L’app e la visualizzazione della pubblica sono accessibili direttamente, anche con profili SSH con chiavi. L’aggiornamento conserva le chiavi e i profili esistenti.

## Modalità UDP

È disponibile per IPv4 locali/remoti e nomi DNS/DDNS. Ogni tocco invia un datagramma con il magic packet standard da 102 byte. Porta configurabile, valore iniziale 9. Nessun polling, servizio permanente o controllo automatico dell'accensione.

## Build e test

JDK 17, Android SDK 35, Gradle 9.7.1, AGP 9.1.1. Dipendenze principali: Material3 1.5.0-alpha03, JSch (fork mwiede) 2.28.8, Bouncy Castle 1.86 per Ed25519 anche su Android.

```sh
ANDROID_HOME=/percorso/android-sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

I test JVM verificano payload UDP, invio/ricezione locale, input, migrazione dei vecchi profili, credenziali con caratteri speciali e rifiuto dell'iniezione di comandi. Il test SSH d'integrazione richiede il server sintetico locale e la variabile WOW_SSH_TEST_EVENTS; altrimenti viene esplicitamente saltato. L'instrumentation personalizzata SshSmokeInstrumentation è destinata solo all'emulatore di sviluppo con fixture locale: prova Keystore, autenticazione/comando SSH e lettura della chiave del server di test senza login, generazione della chiave non esportabile e autenticazione con essa. I test JVM della firma confrontano il formato SSH con un verificatore Java indipendente; la fixture rifiuta chiavi client non autorizzate.
