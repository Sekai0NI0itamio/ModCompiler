package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;

public abstract class ResourceLookupRule<C, V> implements Rule<StringReader, V>, ResourceSuggestion {
    private final NamedRule<StringReader, ResourceLocation> idParser;
    protected final C context;
    private final DelayedException<CommandSyntaxException> error;

    protected ResourceLookupRule(NamedRule<StringReader, ResourceLocation> p_397427_, C p_330414_) {
        this.idParser = p_397427_;
        this.context = p_330414_;
        this.error = DelayedException.create(ResourceLocation.ERROR_INVALID);
    }

    @Nullable
    @Override
    public V parse(ParseState<StringReader> p_332578_) {
        p_332578_.input().skipWhitespace();
        int i = p_332578_.mark();
        ResourceLocation resourcelocation = p_332578_.parse(this.idParser);
        if (resourcelocation != null) {
            try {
                return this.validateElement(p_332578_.input(), resourcelocation);
            } catch (Exception exception) {
                p_332578_.errorCollector().store(i, this, exception);
                return null;
            }
        } else {
            p_332578_.errorCollector().store(i, this, this.error);
            return null;
        }
    }

    protected abstract V validateElement(ImmutableStringReader p_336199_, ResourceLocation p_330230_) throws Exception;
}
