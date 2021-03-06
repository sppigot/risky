package au.gov.amsa.ihs.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import au.gov.amsa.ihs.model.Ship;
import au.gov.amsa.streams.Strings;
import rx.Observable;

public class IhsReaderTest {

    private static final double PRECISION = 0.000001;

    @Test
    public void testReaderFromInputStream() {
        IhsReader r = new IhsReader();
        Observable<Ship> o = r
                .from(IhsReaderTest.class.getResourceAsStream("/ShipData.xml"), "ShipData")
                .map(IhsReader::toShip);
        Ship ship = o.last().toBlocking().single();
        System.out.println(ship);
        assertEquals("4544107", ship.getImo());
        assertEquals("235101211", ship.getMmsi().get());
        assertEquals("Miscellaneous", ship.getType2().get());
        assertEquals("Other Activities", ship.getType3().get());
        assertEquals("Patrol Vessel", ship.getType4().get());
        assertEquals("Patrol Vessel", ship.getType5().get());
        assertEquals(525, (long) ship.getGrossTonnage().get());
        assertEquals("RI", ship.getClassificationSocietyCode().get());
        assertEquals("GBI", ship.getFlagCode().get());
        assertEquals("0860230", ship.getGroupBeneficialOwnerCompanyCode().get());
        assertEquals("GBI", ship.getGroupBeneficialOwnerCountryOfDomicileCode().get());
        assertEquals("FIN", ship.getCountryOfBuildCode().get());
        assertEquals(2002, (int) ship.getYearOfBuild().get());
        assertEquals(1, (int) ship.getMonthOfBuild().get());
        assertEquals(1445.0, ship.getDeadweightTonnage().get(), PRECISION);
        assertEquals("B34H2SQ", ship.getStatCode5().get());
        assertEquals(49.7, ship.getLengthOverallMetres().get(), PRECISION);
        assertEquals(7.5, ship.getBreadthMetres().get(), PRECISION);
        assertFalse(ship.getDisplacementTonnage().isPresent());
        assertEquals(3.7, ship.getDraughtMetres().get(), PRECISION);
        assertEquals(22.0, ship.getSpeedKnots().get(), PRECISION);
        assertEquals("2014-01-16T14:47:29.780Z", ship.getLastUpdateTime().get().toString());
        assertEquals("PROTECTOR", ship.getName().get());
        assertEquals("FIN020050", ship.getShipBuilderCompanyCode().get());
    }

    @Test
    public void testReaderFromZip() {
        Observable<Ship> o = IhsReader.fromZip(new File("src/test/resources/ShipData.xml.zip"))
                .map(x -> IhsReader.toShip(x));
        Ship ship = o.last().toBlocking().single();
        assertEquals("4544107", ship.getImo());
    }

    public static void main(String[] args) {
        // Pattern pattern = Pattern.compile("^.*\\<([^\\>/]*)\\>.*$");
        Pattern pattern = Pattern.compile("^.*name=\"([^\"]*)\".*$");
        System.out.println("public enum Key {");
        Strings.lines(new File("/home/dxm/test.txt"))
                //
                .filter(line -> line.trim().length() > 0)
                //
                .map(line -> {
                    Matcher m = pattern.matcher(line);
                    if (m.find())
                        return m.group(1);
                    else
                        return null;
                })
                //
                .filter(value -> value != null)
                //
                .filter(x -> !x.equals("NewDataSet"))
                //
                .filter(x -> !x.equals("ShipData"))
                //
                .doOnNext(line -> System.out.println("//\n" + line + ","))
                //
                .subscribe();
        System.out.println("}");
    }
}
