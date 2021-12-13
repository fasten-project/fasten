package eu.fasten.core.vulchains;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import java.io.IOException;
import java.lang.reflect.Type;

public class VulnerableCallChainJsonUtils {


    public static Gson getGson() {
        return new GsonBuilder()//
         .registerTypeAdapter(FastenURI.class,
            new TypeAdapter<FastenURI>() {
            @Override
            public void write(JsonWriter jsonWriter, FastenURI fastenURI) throws IOException {
                jsonWriter.value(fastenURI.toString());
            }

            @Override
            public FastenURI read(JsonReader jsonReader) throws IOException {
                final var uri = jsonReader.nextString();
                FastenURI fastenURI;
                if (uri.startsWith("fasten://mvn")) {
                    fastenURI = FastenJavaURI.create(uri);
                }else {
                    fastenURI = FastenURI.create(uri);
                }
                return fastenURI;
            }
        }) //
         .create();
    }

    public static <T> String toJson(T obj) {
        return getGson().toJson(obj);
    }

    public static <T> T fromJson(String json, Type classOfT) {
        return getGson().fromJson(json, classOfT);
    }

}
