package nl.theepicblock.ppetp.test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;

public class Util {
    public static boolean nbtContains(Tag c, String str) {
        return new SnbtPrinterTagVisitor().visit(c).contains(str);
    }

    public static CompoundTag readNbtResource(String name) throws Exception {
        try (var resource = DataFixerTest.class.getResourceAsStream(name)) {
            assert resource != null;
            var str = IOUtils.toString(resource, StandardCharsets.UTF_8);
            return TagParser.parseCompoundFully(str);
        }
    }
}
