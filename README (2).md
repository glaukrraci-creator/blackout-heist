# Blackout Heist

Lojë kompetitive asimetrike 1v1 në Unity: një hajdut kundër sistemit të sigurisë, me presion kohe dhe nivele.

## Ekipi
- Glauk Rraci – QA & Project
- Yllka Dedinca – Game Design, Architecture & Interaction
- Melos Klaiqi – Logic, Algorithm & Software

## Ideja e shkurtër
Hajduti duhet të vjedhë një objekt dhe të dalë para se të mbarojë koha. Mbrojtësi kontrollon rojet, kamerat dhe dyert. Pas çdo raundi rolet ndërrohen. Nivelet shtojnë nga një element të ri: rojet, kamerat, dyert.

## Statusi
Faza II – Product Design (PBT 4, Java 5). Dokumenti aktual: **`Blackout_Heist_GDD_v1.1.pdf`**.

| Faza | Dokumentet |
|---|---|
| PBT 1 | `Lista_e_ideve.pdf`, `Project_Charter_v0.1.pdf` |
| PBT 2 | `PBT2_Canvas_Histori_Rreziqe.pdf`, `Shenim_Serious_Game.pdf`, `Project_Charter_v0.2.pdf` |
| PBT 3 | `Blackout_Heist_GDD_v0.1.docx`, `Blackout_Heist_Paper_Prototype.pdf`, `BlackoutSim.java` |
| PBT 4 | `Blackout_Heist_GDD_v1.1.pdf` (Progression Map, 3 karta nivelesh, Feedback Matrix, Playtest Note, changelog) |

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
- Playtest me ekip tjetër: **nuk është bërë ende** (shablloni në GDD v1.1, seksioni 6.2).

## Backlog
| Detyra | Përgjegjësi |
|---|---|
| Loja në Unity: skena 2D, WASD + maus, koni i shikimit, vizual/audio i feedback-ut | Yllka |
| AI e mbrojtësit (tani vetëm skenarë), fog of war, gjenerimi i hartave me BFS | Melos |
| Playtest me ekip tjetër, balancimi i pikëve (hajduti deri ~326, mbrojtësi ~189) | Glauk |
| Dyert me kod (të shtyra; niveli 3 përdor dyert e zakonshme) | Ekipi |

## Çfarë nuk përfshihet
Multiplayer në rrjet, grafikë 3D, histori/narrativë, tingull i personalizuar, ruajtje në server.
