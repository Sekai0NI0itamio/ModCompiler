package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;

public class ResourceLocationParseRule implements Rule<StringReader, ResourceLocation> {
    public static final Rule<StringReader, ResourceLocation> INSTANCE = new ResourceLocationParseRule();

    private ResourceLocationParseRule() {
    }

    @Nullable
    public ResourceLocation parse(ParseState<StringReader> p_391838_) {
        p_391838_.input().skipWhitespace();

        try {
            return ResourceLocation.readNonEmpty(p_391838_.input());
        } catch (CommandSyntaxException commandsyntaxexception) {
            return null;
        }
    }
}
