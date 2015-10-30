package de.schildbach.wallet.data;

import android.os.Parcel;
import android.os.Parcelable;
import se.rosenbaum.jpop.PopRequestURI;

public class PopIntent implements Parcelable {
    PopRequestURI popRequestURI;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(popRequestURI.toURIString());
    }

    public static final Parcelable.Creator<PopIntent> CREATOR = new Parcelable.Creator<PopIntent>() {
        public PopIntent createFromParcel(Parcel in) {
            return new PopIntent(in);
        }

        public PopIntent[] newArray(int size) {
            return new PopIntent[size];
        }
    };


    private PopIntent(Parcel in) {
        popRequestURI = new PopRequestURI(in.readString());
    }

    private PopIntent(PopRequestURI popRequestURI) {
        this.popRequestURI = popRequestURI;
    }

    public static PopIntent fromPopRequestURI(PopRequestURI popRequestURI) {
        return new PopIntent(popRequestURI);
    }

    public PopRequestURI getPopRequestURI() {
        return popRequestURI;
    }
}
