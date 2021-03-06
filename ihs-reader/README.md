ihs-reader
==================
Reads ship static data from [IHS (LLoyds)](http://www.shipfinder.org/) that AMSA 
receives monthly as a zip file containing multiple xml files.

How to use
-------------
```java
import au.gov.amsa.ihs.reader.IhsReader;

Observable<Ship> ships = IhsReader.fromZip(file);
```

Read into memory as a map so you can do lookups by IMO number:
```java
Observable<Map<String, Ship>> shipsByImo = IhsReader.fromZipAsMap(file);
```

Performance
-----------------
10 seconds to read 116,000 ships into memory from an 84MB zip file. 
