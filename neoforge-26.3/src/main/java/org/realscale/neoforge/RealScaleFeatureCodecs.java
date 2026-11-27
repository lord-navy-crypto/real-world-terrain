package org.realscale.neoforge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
final class RealScaleFeatureCodecs {
 private RealScaleFeatureCodecs() {}
 static <E extends Enum<E>, F> MapCodec<F> enumField(String field, Class<E> enumType,
        Function<E,F> factory, Function<F,E> getter) {
  return Codec.STRING.fieldOf(field).xmap(
      name -> factory.apply(Enum.valueOf(enumType, name)),
      value -> getter.apply(value).name());
 }
}
