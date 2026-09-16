#!/bin/bash
IMAGES=(
    "1506744626753-94c00bc20cb9" # dark textured
    "1518818479052-41e974e64f7b" # abstract dark
    "1557672172-298e090bd0f1" # abstract blue/red
    "1550684848-fac1c5b4e853" # dark mountain/stars
    "1579546929518-9e396f3cc809" # colorful fluid
    "1528459801415-3eba29e46462" # colorful abstract
    "1618005182384-a83a8bd57fbe" # dark neon
    "1534081333815-bfa3b331006e" # nature green
    "1500462918059-b1a0cb512f1d" # vivid sunset
    "1478760327748-1b978f1b32d2" # ocean
    "1464802686167-b939a6910659" # galaxy
    "1507608616759-54f48f0af0ee" # rain drops
    "1604871000636-074fa5117945" # neon grid
    "1558591710-4b4a1ae0f046" # dark polygon
    "1614850523459-c2f4c699c52e" # 3d shapes
    "1618501254338-f9b2dcbce76e" # holographic
    "1511447333015-45b65e60f6d5" # geometric
    "1553095066-5014bc7b7f2d" # minimal grey
    "1497250681960-ef046c08a56e" # forest mist
    "1486789182346-6c1edc0cb2bc" # cherry blossom
)

for i in "${!IMAGES[@]}"; do
    ID="${IMAGES[$i]}"
    URL="https://images.unsplash.com/photo-${ID}?q=80&w=800&auto=format"
    echo "Downloading image $((i+1))/20: $ID"
    curl -sL "$URL" -o "app/src/main/assets/stock_images/stock_$i.jpg"
done
echo "Done!"
