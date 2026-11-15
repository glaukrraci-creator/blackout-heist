# Blackout Heist

Lojë kompetitive asimetrike 1v1 në Unity: një hajdut kundër sistemit të sigurisë, me presion kohe dhe nivele.

## Ekipi
- Glauk Rraci – QA & Project
- Yllka Dedinca – Game Design, Architecture & Interaction
- Melos Klaiqi – Logic, Algorithm & Software

## Ideja e shkurtër
Hajduti duhet të vjedhë një objekt dhe të dalë para se të mbarojë koha. Mbrojtësi kontrollon rojet, kamerat dhe dyert. Pas çdo raundi rolet ndërrohen. Nivelet shtojnë nga një element të ri: rojet, kamerat, dyert.

## Statusi
Faza III – Architecture (PBT 7, Java 8). Dokumentet aktuale: **`Blackout_Heist_Architecture_v1.0.pdf`** dhe **`Blackout_Heist_GDD_v1.1.pdf`**.

| Faza | Dokumentet |
|---|---|
| PBT 1 | `Lista_e_ideve.pdf`, `Project_Charter_v0.1.pdf` |
| PBT 2 | `PBT2_Canvas_Histori_Rreziqe.pdf`, `Shenim_Serious_Game.pdf`, `Project_Charter_v0.2.pdf` |
| PBT 3 | `Blackout_Heist_GDD_v0.1.pdf` (edhe `.docx`), `Blackout_Heist_Paper_Prototype.pdf`, `BlackoutSim.java` |
| PBT 4 | `Blackout_Heist_GDD_v1.1.pdf` (Progression Map, 3 karta nivelesh, Feedback Matrix, Playtest Note, changelog) |
| PBT 5 | `Cilesia_Rabin_Kap3.1.pdf` (cilësia sipas Rabin 3.1 dhe zbatimi në projekt) |
| PBT 6 | `Mbyll_Deren_Specifikimi.pdf`, `csharp/DoorMechanic.cs` (mekanika "Mbyll derën" në C#, me teste) |
| PBT 7 | `Blackout_Heist_Architecture_v1.0.pdf` (diagrami, interface notes, code review record), vertical slice v0.1, `Blackout_Heist_Prezantimi_Vleresimi.pdf`, video `Blackout_Heist_Demo.mp4` |

## Vertical Slice v0.1 (luhet)
Windows: klik dy herë `run.bat`. macOS/Linux: `./run.sh`. Ose:
```
javac -encoding UTF-8 BlackoutSim.java Progress.java BlackoutDemo.java
java BlackoutDemo
```
WASD/shigjetat lëviz, SPACE prit, R rifillo, N niveli tjetër. Progresi ruhet në `~/.blackout_heist_progress.properties`.

| Moduli | Skedari |
|---|---|
| Input + Rendering/UI (engine-dependent) | `BlackoutDemo.java` |
| Domain/game logic + State (source of truth) | `BlackoutSim.java` |
| Persistence | `Progress.java` (testet: `java Progress`) |
| Utilities (A*, vija e shikimit, verifikuesi BFS, nivelet) | `BlackoutSim.java` |

## Simulimi i rregullave (Java)
Loja ndërtohet në Unity. `BlackoutSim.java` është simulimi dhe specifikimi i ekzekutueshëm i rregullave: vlerat, nivelet dhe testet e tij janë referenca për skenën në Unity.
```
javac BlackoutSim.java
java BlackoutSim          # 23 teste (rezultati në test_output.txt)
java BlackoutSim levels   # shtyp çdo nivel: harta, zgjidhja, gjurma dhe mesazhet
```
Kërkon Java 17+.

## Çfarë ekziston në simulim
- Simulim i plotë i rregullave R1–R16 (pa grafikë, në konsolë), 1 tick = 1 sekondë.
- **A\*** për lëvizjen e rojeve; roja pret me mesazh kur një derë e mbyllur i bllokon rrugën.
- Detektim me vijë shikimi (rojë 3, kamerë 4, muret dhe dyert e mbyllura e bllokojnë).
- Mesazhe feedback-u me shkak (`ZBULUAR nga roja 1…`, `REFUZUAR: Buxhet i pamjaftueshëm (1 < 2)`, …).
- Gjurma e rrugës dhe ekrani përmbledhës (arsyeja, pikët, sekonda e zbulimit).
- Zona e sigurt (5 s pa veprime të mbrojtësit) dhe veprimet e lejuara sipas nivelit.
- 3 nivele me mbrojtës referencë (skenar) dhe zgjidhje të ruajtura.
- Verifikues BFS mbi gjendjet e lojës: provon që çdo nivel ka zgjidhje dhe që rruga naive humb.
- **23/23 teste kalojnë** (T01–T15 nga GDD v0.1, T16–T23 të reja).

## Çfarë u provua
- Të tri nivelet: zgjidhja fiton (23 s / 44 s / 43 s), rruga më e shkurtër naive kapet.
- Verifikuesi gjeti që harta e prototipit të letrës nuk kishte zgjidhje (rojet mbyllnin hyrjen me shikim); rojet u zhvendosën.



