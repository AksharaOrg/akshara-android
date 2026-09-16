#!/bin/bash
for i in {0..19}; do
    echo "Downloading image $((i+1))/20"
    curl -sL "https://picsum.photos/seed/$i/400/600" -o "app/src/main/assets/stock_images/stock_$i.jpg"
done
echo "Done!"
