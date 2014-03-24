      var stage = new Kinetic.Stage({
        container: 'container',
        width: 578,
        height: 200
      });

      var layer = new Kinetic.Layer();

      var rect = new Kinetic.Rect({
        x: 239,
        y: 75,
        width: 100,
        height: 50,
        fill: 'green',
        stroke: 'black',
        strokeWidth: 4
      });

      // add the shape to the layer
      layer.add(rect);

      // add the layer to the stage
      stage.add(layer);