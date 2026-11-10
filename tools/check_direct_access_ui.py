"""Regression checks for direct app/key access on the development emulator."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = ["/opt/android-sdk/platform-tools/adb", "-s", "emulator-5554"]

def run(*args):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, check=True, timeout=40).stdout

def snapshot():
    for _ in range(3):
        result = run("shell", "uiautomator", "dump", "/sdcard/wow-direct-access.xml")
        if "dumped to" in result:
            return ET.fromstring(run("shell", "cat", "/sdcard/wow-direct-access.xml"))
        time.sleep(0.5)
    raise AssertionError("UI dump failed")

def texts(root):
    return [n.get("text", "") for n in root.iter("node") if n.get("text")]

def wait_text(text):
    for _ in range(6):
        root = snapshot()
        if any(text in t for t in texts(root)):
            return root
        time.sleep(0.3)
    raise AssertionError("Missing UI text: " + text)

def tap(text):
    root = wait_text(text)
    node = next(n for n in root.iter("node") if n.get("text") == text or n.get("content-desc") == text)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    run("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))

def home():
    root = wait_text("Chiave SSH del dispositivo")
    assert "Impostazioni" not in texts(root)
    assert "Sblocca WoW" not in texts(root)
    assert "App protetta" not in texts(root)
    return root

def fixture(name):
    output = run("shell", "am", "instrument", "-w", "-e", "case", name,
                 "net.alessio.wow.test/net.alessio.wow.SshSmokeInstrumentation")
    assert "PASS:" in output, output
    return output

def start():
    run("shell", "am", "start", "-n", "net.alessio.wow/.MainActivity")

def main():
    before = fixture("ui-key")
    start()
    root = snapshot()
    if "Device SSH key" in texts(root):
        menu = next(n for n in root.iter("node") if n.get("content-desc") == "Menu")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", menu.get("bounds")))
        run("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
        tap("Italiano")
        wait_text("Licenze di terze parti")
        run("shell", "input", "keyevent", "4")
    assert "Profilo SSH prova" in texts(home())
    print("PASS home opens directly with an SSH key profile and no settings button", flush=True)
    tap("Chiave SSH del dispositivo")
    root = wait_text("Copia pubblica")
    public = next(t for t in texts(root) if t.startswith("ecdsa-sha2-nistp256 "))
    assert "Disinstallando WoW perderai questa chiave." in texts(root)
    assert not any("Incolla l’intera" in t or "laptop" in t for t in texts(root))
    print("PASS public key opens directly; only uninstall warning follows the key", flush=True)
    tap("Copia pubblica")
    root = snapshot()
    assert next(t for t in texts(root) if t.startswith("ecdsa-sha2-nistp256 ")) == public
    tap("Chiudi")
    run("shell", "input", "keyevent", "3")
    start()
    home()
    print("PASS returning from background needs no authentication", flush=True)
    tap("Modifica")
    tap("Salva")
    assert "Profilo SSH prova" in texts(home())
    print("PASS existing SSH key profile saves without a device lock requirement", flush=True)
    after = fixture("ui-cleanup")
    assert before.split("fingerprint ")[1] == after.split("fingerprint ")[1]
    print("PASS SSH key remains unchanged", flush=True)

if __name__ == "__main__":
    main()
