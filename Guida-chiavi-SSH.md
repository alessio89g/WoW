# WoW: autenticazione SSH tramite chiave

## Smartphone Android

1. Installa la versione aggiornata di WoW sopra la precedente, senza disinstallare.
2. Nella home apri **Chiave SSH del dispositivo**.
3. Premi **Genera chiave**, poi **Copia pubblica**. Se la chiave esiste già, l'app mostra la stessa senza sostituirla.
4. Nell'ASUS apri **Amministrazione > Sistema > Servizio > Chiavi autorizzate SSH** (la traduzione può essere Chiave di autenticazione SSH). Incolla l'intera riga che inizia con `ecdsa-sha2-nistp256`, aggiungendola alle eventuali righe esistenti, e applica. Per ora lascia abilitato il login con password.
5. In WoW modifica il profilo Router SSH: scegli **Chiave dispositivo** in Autenticazione, mantieni l'utente SSH del router e salva. Il profilo con chiave non salva una password e non ripiega sul login con password.
6. Verifica la chiave del server se non l'avevi già associata, poi premi Risveglia a PC acceso. Il messaggio di comando eseguito conferma l'accesso e l'esecuzione del comando; non certifica il risveglio a PC spento.
7. Quando l'accesso con chiave è riuscito, puoi disabilitare **Consenti login con password** nell'ASUS. Questa impostazione vale anche per la LAN. Non chiudere eventuali sessioni SSH di recupero prima di avere verificato un nuovo collegamento con chiave.
8. Per il collegamento diretto da rete mobile, SSH deve essere abilitato su LAN e WAN. Il profilo usa il DDNS e la porta SSH effettiva, indicata nelle impostazioni del router.

La chiave privata ECDSA P-256 viene generata in Android Keystore e usata per firmare, senza esportarla dall'app o salvarla in un file. La pubblica è copiabile; le credenziali private non devono essere inviate in chat. La protezione hardware dipende dal dispositivo: questa versione non attesta né forza StrongBox. L’app e la schermata della chiave pubblica si aprono direttamente, senza richieste di autenticazione del dispositivo.

Disinstallando l'app o cancellandone i dati perdi la chiave del dispositivo. Su un altro telefono verrà generata una chiave diversa, da autorizzare di nuovo sul router. L'app non offre esportazione o rigenerazione distruttiva della chiave esistente.

La chiave del dispositivo (autenticazione del client) è diversa dalla chiave del router (riconoscimento del server) già verificata nelle versioni precedenti.

## Laptop Windows, se vuoi mantenere l'accesso SSH

Il laptop deve avere una propria coppia di chiavi. Non si può copiare su Windows la privata non esportabile di WoW.

In PowerShell:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.ssh" | Out-Null
if (Test-Path "$env:USERPROFILE\.ssh\asus_wow") {
    Write-Host "La chiave asus_wow esiste già: non viene sovrascritta."
} else {
    ssh-keygen -t ed25519 -a 64 -f "$env:USERPROFILE\.ssh\asus_wow" -C "Laptop-ASUS"
}
Get-Content "$env:USERPROFILE\.ssh\asus_wow.pub"
```

Scegli una passphrase per proteggere la privata del laptop. Aggiungi anche la riga pubblica del laptop all'elenco delle chiavi autorizzate nell'ASUS, su una riga separata. Non incollare né condividere il file senza estensione `.pub`.

Prima di disabilitare le password verifica un collegamento che ammetta soltanto la tua chiave:

```powershell
ssh -p 22 -i "$env:USERPROFILE\.ssh\asus_wow" -o IdentitiesOnly=yes -o PreferredAuthentications=publickey -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no utente@192.0.2.1
```

La richiesta della passphrase della chiave locale è normale: è diversa dalla password dell'account del router. Se l'utente amministratore ha un altro nome, sostituisci `utente`.

Autorizzare anche il laptop significa consentire l'accesso a entrambi i dispositivi, non esclusivamente allo smartphone. Se vuoi che solo WoW acceda, autorizza soltanto la sua chiave e mantieni un metodo amministrativo alternativo per recuperare l'accesso.


## Linux (esempio con Manjaro)

Il PC Manjaro deve avere una propria coppia di chiavi, distinta da quelle di WoW e del laptop Windows. La chiave di WoW non è esportabile su Manjaro.

### 1. Genera la chiave sul PC Linux

Apri il terminale ed esegui questo blocco. Se esiste già una chiave con lo stesso nome, non viene sovrascritta:

```bash
mkdir -p "$HOME/.ssh"
chmod 700 "$HOME/.ssh"
if [ -e "$HOME/.ssh/asus_wow" ] || [ -e "$HOME/.ssh/asus_wow.pub" ]; then
    printf '%s\n' 'Esiste già un file asus_wow o asus_wow.pub: nessun file viene sovrascritto.'
else
    ssh-keygen -t ed25519 -a 64 -f "$HOME/.ssh/asus_wow" -C "Manjaro-ASUS"
fi
if [ -f "$HOME/.ssh/asus_wow.pub" ]; then
    cat "$HOME/.ssh/asus_wow.pub"
fi
```

Quando `ssh-keygen` chiede una passphrase, scegline una per proteggere il file privato. Il terminale non mostra i caratteri mentre la digiti. La coppia sarà composta da:

- `~/.ssh/asus_wow`: chiave privata, da conservare sul PC senza condividerla.
- `~/.ssh/asus_wow.pub`: chiave pubblica da aggiungere al router.

Se esiste la privata ma manca il file `.pub`, puoi visualizzare la pubblica senza rigenerare né sostituire la coppia:

```bash
ssh-keygen -y -f "$HOME/.ssh/asus_wow"
```

### 2. Autorizza la pubblica nell'ASUS

Nella GUI del router vai in **Amministrazione > Sistema > Servizio > Chiavi autorizzate SSH**. Aggiungi la riga pubblica di Manjaro alle eventuali chiavi di WoW e Windows e premi **Applica**.

Usa **una chiave completa per riga**, separando le chiavi con un ritorno a capo. Non usare virgole o punti e virgola e non spezzare manualmente una chiave a metà. Il semplice ritorno a capo visivo del campo non è un problema.

Se il login con password è già disabilitato, puoi aggiungere la pubblica dalla GUI del router: non è necessario riabilitare le password SSH.

### 3. Prova l'accesso con la sola chiave sulla LAN

Nel terminale Manjaro:

```bash
ssh -p 22 -i "$HOME/.ssh/asus_wow" -o IdentitiesOnly=yes -o PreferredAuthentications=publickey -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no utente@192.0.2.1
```

Le opzioni impediscono di ripiegare sull'autenticazione con password del router. La richiesta della **passphrase della chiave locale** è invece normale. Se il nome utente amministratore o la porta sono diversi, sostituisci `utente` o `22`.

Al primo collegamento controlla l'impronta della chiave del router prima di confermarla. Se il server è già conosciuto, una segnalazione di chiave cambiata va verificata: non eliminare automaticamente la vecchia associazione.

### 4. Accesso da Internet

Quando Manjaro è su una rete esterna e SSH dell'ASUS è abilitato su **LAN e WAN**, usa il DDNS:

```bash
ssh -p 22 -i "$HOME/.ssh/asus_wow" -o IdentitiesOnly=yes -o PreferredAuthentications=publickey -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no utente@router.example.net
```

La porta è TCP e coincide con quella SSH esposta dal router. Per il servizio SSH dell'ASUS non serve una regola di port forwarding verso il PC. Il DDNS può richiedere una nuova verifica della chiave del server perché è un nome diverso dall'IP LAN: per lo stesso router e algoritmo l'impronta deve coincidere.

### 5. Dopo la verifica

Se le password SSH sono ancora abilitate, disabilitale soltanto dopo avere verificato un nuovo collegamento con chiave da tutti i dispositivi che vuoi mantenere autorizzati. Questa impostazione vale sia sulla LAN sia da Internet.

Per revocare un dispositivo, rimuovi dall'ASUS soltanto la sua riga pubblica. Conserva in modo protetto la privata di Manjaro e la relativa passphrase se vuoi ripristinarle in futuro. A differenza della privata di WoW in Android Keystore, questa privata è un file trasferibile e va trattata come una credenziale segreta.


## Accesso all’app (WoW 0.5.0)

Il blocco di accesso dell’app è stato rimosso completamente, insieme alla relativa voce Impostazioni. Puoi aprire WoW, visualizzare e copiare la chiave pubblica e usare i profili SSH senza richieste di PIN, password, sequenza o biometria del dispositivo. Non serve un blocco schermo per creare profili SSH con chiave.

L’aggiornamento conserva la chiave privata non esportabile in Android Keystore e i profili cifrati esistenti. Non occorre autorizzare una nuova pubblica nei router. Installa il nuovo APK sopra la versione precedente: disinstallando WoW perderai la chiave.

## Più router e abitazioni

Puoi autorizzare la medesima chiave pubblica di WoW su più router ASUS. Crea un profilo distinto per ciascun router/PC, impostando il relativo host LAN o DDNS, porta SSH, utente, interfaccia LAN e MAC del PC. La verifica della chiave del server è separata per ogni profilo: due router diversi hanno normalmente impronte diverse.

L’accesso tramite SSH esegue direttamente `ether-wake` passando il MAC impostato nel profilo WoW. Non dipende dalla presenza del PC nell’elenco salvato della pagina Wake-on-LAN ASUS. Devono invece restare abilitati il risveglio nel BIOS/UEFI e nella scheda di rete del PC, il servizio SSH del router e l’autorizzazione della pubblica. Per l’accesso da Internet serve inoltre SSH raggiungibile dalla WAN; questa modalità non richiede l’inoltro della porta UDP WoL.
