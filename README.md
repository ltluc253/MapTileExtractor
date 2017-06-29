# MapTileExtractor
Map Tile Extractor
------------------
This project is use to fetch raster tiles from a tile server and pack them into a mbtiles file

Input params:
-------------
- -zlRange : zoom level range, it is two number with lower number is placed first
- -threads: num of threads will run for this task, default value is 100
- -endpoint: map tile end point 
- -country: search key for country 
- -city: search key for city
- -compressFactor: 0~1, this param only support for jpg tile
- -tileListPath: path of tile list https://openmaptiles.com/downloads/list.json 
- -storageTilePath: storage path of crawler map tiles, default value is MapData
- -extractedPath: storage path of extracted mbtiles file, default value is Extracted
- -o: output file name it does not include mbtiles extension 
- -bBox: the box of region which is extracted, the format is [leftLng bottomLat rightLng topLat] 
- -onlyCrawler: 1 is only crawler map tiles, otherwise will extract to mbtiles file 
Note: input -o and -bBox will extract custom area, the params -country, -city, -tileListPath won't be effect
