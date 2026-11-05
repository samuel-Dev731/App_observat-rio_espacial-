# Third-party astronomy data

## Stellarium western constellation figures
The 88 constellation line figures are derived from Stellarium's western sky culture `constellationship.fab` (HIP star-number pairs). Stellarium sky-culture data is distributed under **GPL-2.0-or-later**. Source: https://github.com/Stellarium/stellarium/tree/v0.21.3/skycultures/western

## HYG star catalogue
Star positions and visual magnitudes are derived from the HYG Database (Astronexus), licensed **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Source: https://github.com/astronexus/HYG-Database

## Generated catalogue file
`app/src/main/cpp/constellation_catalog.h` and `app/src/main/assets/constellation_catalog.json` are generated catalogue data based on the sources above. The source generator used for the packed representation is available at https://github.com/dmellok/scope-clock/blob/main/tools/gen_stars.py and is marked GPL-2.0-or-later. Preserve these notices and comply with the respective licenses when redistributing.
